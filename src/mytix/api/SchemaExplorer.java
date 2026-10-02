package mytix.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import mytix.database.DatabaseLogistics;
import static mytix.api.ApiData.*;

/** Identifiers originate only from this fixed project allowlist and database metadata. */
final class SchemaExplorer {
    static final List<String> TABLES=List.of("postal_areas","postal_adjacencies","venues","sections","seats",
        "segments","genres","artists","events","event_artists","performances","price_tiers","performance_section_tiers",
        "blocked_seats","users","payment_cards","orders","tickets","ticket_ownership","resale_listings","reviews","review_noun_phrases");
    static final Set<String> REFERENCE=Set.of("postal_areas","postal_adjacencies","venues","sections","seats","segments","genres","artists");
    record Column(String name,String type,boolean nullable,boolean generated,boolean primary,boolean indexed,boolean privateValue) {}
    record Relation(String name,String source,String column,String target,String targetColumn) {}
    record Entity(String name,List<Column> columns,List<String> primaryKey,String group,boolean editable) {}
    private final Map<String,Entity> entities=new LinkedHashMap<>();
    private final List<Relation> relations=new ArrayList<>();
    SchemaExplorer() throws SQLException {
        try(Connection c=DatabaseLogistics.getConnection()) {
            for(String table:TABLES) {
                List<String> primary=new ArrayList<>();Set<String> indexed=new HashSet<>();List<Column> columns=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT COLUMN_NAME,INDEX_NAME,SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY INDEX_NAME,SEQ_IN_INDEX")) {
                    ps.setString(1,table);try(ResultSet r=ps.executeQuery()){while(r.next()) {if(r.getString(2).equals("PRIMARY"))primary.add(r.getString(1));if(r.getInt(3)==1)indexed.add(r.getString(1));}}
                }
                try(PreparedStatement ps=c.prepareStatement("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
                    ps.setString(1,table);try(ResultSet r=ps.executeQuery()){while(r.next()){String name=r.getString(1); columns.add(new Column(name,r.getString(2),r.getString(3).equals("YES"),r.getString(4).contains("GENERATED")||r.getString(4).contains("auto_increment"),primary.contains(name),indexed.contains(name),sensitive(name)));}}
                }
                if(columns.isEmpty()||primary.isEmpty())throw new IllegalStateException("Install the current 22-table MyTix schema before starting the API.");
                String group=TABLES.indexOf(table)<5?"Places":TABLES.indexOf(table)<14?"Programming":TABLES.indexOf(table)<16?"Accounts":"Transactions";
                entities.put(table,new Entity(table,List.copyOf(columns),List.copyOf(primary),group,REFERENCE.contains(table)||table.equals("events")));
            }
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CONSTRAINT_NAME,TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION")) {
                while(r.next())if(entities.containsKey(r.getString(2))&&entities.containsKey(r.getString(4)))relations.add(new Relation(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)));
            }
        }
    }
    Object schema(){return Map.of("entities",entities.values(),"relations",relations);}
    Entity entity(String name){Entity e=entities.get(name);if(e==null)throw new Failure(400,"Unknown project table.");return e;}
    static boolean sensitive(String c){return c.equals("passwordHash")||c.toLowerCase(Locale.ROOT).contains("cardnumber");}
    static String q(String s){if(!s.matches("[A-Za-z_][A-Za-z_0-9]*"))throw new IllegalArgumentException("Invalid identifier");return "`"+s+"`";}
    private String scope(String table,Actor a,List<Object> values) {
        if(!Set.of("users","payment_cards","orders","tickets","ticket_ownership","resale_listings").contains(table))return "1=1";
        if(a==null)return "1=0";
        values.add(a.id());
        return switch(table) {
            case "users" -> "t.userID=? AND t.deletedAt IS NULL";
            case "payment_cards" -> "t.cardNumber IN (SELECT creditCardNumber FROM users WHERE userID=? AND deletedAt IS NULL)";
            case "orders" -> "t.customerID=?";
            case "tickets" -> "t.ticketID IN (SELECT ticketID FROM ticket_ownership WHERE ownerID=?)";
            case "ticket_ownership" -> "t.ownerID=?";
            case "resale_listings" -> {values.add(a.id());yield "(t.sellerID=? OR t.buyerID=?)";}
            default -> throw new IllegalStateException();
        };
    }
    Object page(JsonNode input,Actor actor) throws Exception {
        Entity e=entity(required(input,"table"));int limit=integer(input,"limit",25);if(limit<1||limit>100)throw new Failure(400,"Page size must be 1–100.");
        List<Object> bind=new ArrayList<>();String where=scope(e.name(),actor,bind);String filter=text(input,"filterColumn");
        if(!filter.isBlank()) {
            Column col=e.columns().stream().filter(c->c.name().equals(filter)&&c.indexed()&&!c.privateValue()).findFirst().orElseThrow(()->new Failure(400,"Choose an indexed, visible filter column."));
            where+=" AND t."+q(col.name())+"=?";bind.add(required(input,"filterValue"));
        }
        List<Object> baseBind=new ArrayList<>(bind);String baseWhere=where;String cursor=text(input,"cursor");
        if(!cursor.isBlank()) {
            if(cursor.length()>4096)throw new Failure(400,"Invalid cursor.");
            JsonNode keys=WebServer.JSON.readTree(Base64.getUrlDecoder().decode(cursor));
            if(!keys.isArray()||keys.size()!=e.primaryKey().size())throw new Failure(400,"Invalid cursor.");
            List<String> terms=new ArrayList<>();
            // Lexicographic expansion lets MySQL use the primary index, including composite keys.
            for(int i=0;i<e.primaryKey().size();i++){List<String> and=new ArrayList<>();for(int j=0;j<=i;j++){and.add("t."+q(e.primaryKey().get(j))+(j==i?">?":"=?"));bind.add(keys.get(j).asText());}terms.add("("+String.join(" AND ",and)+")");}
            where+=" AND ("+String.join(" OR ",terms)+")";
        }
        List<List<String>> rows=new ArrayList<>();List<String> lastKey=null;long total;
        try(Connection c=DatabaseLogistics.getConnection()) {
            try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM "+q(e.name())+" t WHERE "+baseWhere)){bind(ps,baseBind);try(ResultSet r=ps.executeQuery()){r.next();total=r.getLong(1);}}
            String sql="SELECT "+e.columns().stream().map(col->col.name().equals("passwordHash")?"NULL AS `passwordHash`":"t."+q(col.name())).collect(Collectors.joining(","))+" FROM "+q(e.name())+" t WHERE "+where+" ORDER BY "+e.primaryKey().stream().map(k->"t."+q(k)).collect(Collectors.joining(","))+" LIMIT ?";
            bind.add(limit+1);
            try(PreparedStatement ps=c.prepareStatement(sql)) {bind(ps,bind);try(ResultSet r=ps.executeQuery()){while(r.next()) {
                List<String> row=new ArrayList<>();for(Column col:e.columns()){String v=r.getString(col.name());row.add(col.privateValue()?(v==null?"[private]":"•••• "+v.substring(Math.max(0,v.length()-4))):v);}
                rows.add(row);if(rows.size()==limit){lastKey=new ArrayList<>();for(String key:e.primaryKey())lastKey.add(r.getString(key));}
            }}}
        }
        boolean more=rows.size()>limit;if(more)rows.remove(rows.size()-1);
        String next=more?Base64.getUrlEncoder().withoutPadding().encodeToString(WebServer.JSON.writeValueAsBytes(lastKey)):"";
        return Map.of("columns",e.columns().stream().map(Column::name).toList(),"rows",rows,"total",total,"nextCursor",next,"scope",Set.of("users","payment_cards","orders","tickets","ticket_ownership","resale_listings").contains(e.name())?"Your account":"Shared catalog");
    }
    static void bind(PreparedStatement ps,List<Object> values)throws SQLException {ps.setQueryTimeout(20);for(int i=0;i<values.size();i++)ps.setObject(i+1,values.get(i));}
    Object status()throws SQLException {
        Map<String,Long> counts=new LinkedHashMap<>();try(Connection c=DatabaseLogistics.getConnection();Statement s=c.createStatement()){for(String table:TABLES){try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+q(table))){r.next();counts.put(table,r.getLong(1));}}}
        return Map.of("counts",counts,"today",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),"timezone","UTC");
    }
    Object reference(JsonNode input,Actor actor,boolean delete)throws Exception {
        role(actor,"ORGANIZER");Entity e=entity(required(input,"table"));
        if(!REFERENCE.contains(e.name())&&!e.name().equals("events"))throw new Failure(400,"Use the dedicated workflow to change this entity.");
        if(e.name().equals("events")&&!delete)throw new Failure(400,"Use Create event.");
        JsonNode values=input.path("values");if(!values.isObject())throw new Failure(400,"Row values are required.");
        List<String> columns=new ArrayList<>();List<Object> bind=new ArrayList<>();
        for(Iterator<String> it=values.fieldNames();it.hasNext();) {
            String key=it.next();Column col=e.columns().stream().filter(c->c.name().equals(key)).findFirst().orElseThrow(()->new Failure(400,"Unknown column."));
            if(col.privateValue()||(!delete&&col.generated())||(delete&&!e.primaryKey().contains(key)))throw new Failure(400,"This column cannot be supplied.");
            columns.add(key);bind.add(values.get(key).isNull()?null:values.get(key).asText());
        }
        if(columns.isEmpty()||(delete&&!new HashSet<>(columns).equals(new HashSet<>(e.primaryKey()))))throw new Failure(400,"Provide every primary key for deletion.");
        try(Connection c=DatabaseLogistics.getConnection()) {
            c.setAutoCommit(false);
            try {
                if(e.name().equals("events")&&delete) {
                    int event=id(values,"eventID");
                    try(PreparedStatement ps=c.prepareStatement("SELECT eventID FROM events WHERE eventID=? AND organizerID=? FOR UPDATE")) {
                        ps.setInt(1,event);ps.setInt(2,actor.id());try(ResultSet r=ps.executeQuery()){if(!r.next())throw new Failure(404,"Row not found in your permitted scope.");}
                    }
                    try(PreparedStatement ps=c.prepareStatement("SELECT performanceID FROM performances WHERE eventID=? LIMIT 1")) {
                        ps.setInt(1,event);try(ResultSet r=ps.executeQuery()){if(r.next())throw new Failure(409,"This event has performances. Preserve its history and use performance cancellation.");}
                    }
                    try(PreparedStatement ps=c.prepareStatement("DELETE FROM event_artists WHERE eventID=?")){ps.setInt(1,event);ps.executeUpdate();}
                }
                // Once a venue has performances, alter inventory through organizer workflows only.
                if(e.name().equals("sections")||e.name().equals("seats")) {
                    String query=e.name().equals("sections")?(delete?"SELECT venueID FROM sections WHERE sectionID=?":"SELECT venueID FROM venues WHERE venueID=?"):
                        (delete?"SELECT s.venueID,s.isGeneralAdmission FROM seats se JOIN sections s USING(sectionID) WHERE se.seatID=?":"SELECT venueID,isGeneralAdmission FROM sections WHERE sectionID=?");
                    int key=id(values,e.name().equals("sections")?(delete?"sectionID":"venueID"):(delete?"seatID":"sectionID"));int venue;
                    try(PreparedStatement ps=c.prepareStatement(query)){ps.setInt(1,key);try(ResultSet r=ps.executeQuery()){if(!r.next())throw new Failure(400,"Parent row does not exist.");venue=r.getInt(1);if(e.name().equals("seats")&&!delete&&r.getBoolean(2))throw new Failure(400,"Standing sections do not have physical seats.");}}
                    try(PreparedStatement ps=c.prepareStatement("SELECT performanceID FROM performances WHERE venueID=? LIMIT 1")){ps.setInt(1,venue);try(ResultSet r=ps.executeQuery()){if(r.next())throw new Failure(409,"This venue already has performances; its physical layout is locked.");}}
                }
                String sql;
                if(delete){sql="DELETE FROM "+q(e.name())+" WHERE "+columns.stream().map(k->q(k)+"=?").collect(Collectors.joining(" AND "));if(e.name().equals("events")){sql+=" AND organizerID=?";bind.add(actor.id());}}
                else sql="INSERT INTO "+q(e.name())+" ("+columns.stream().map(SchemaExplorer::q).collect(Collectors.joining(","))+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
                int changed;try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(ps,bind);changed=ps.executeUpdate();}
                if(changed!=1)throw new Failure(404,"Row not found in your permitted scope.");c.commit();return ok(changed);
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
