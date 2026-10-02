package mytix.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

final class ApiData {
    record Table(List<String> columns, List<List<String>> rows) {
        Table {if(rows.stream().anyMatch(row->row.size()!=columns.size()))throw new IllegalStateException("Result columns do not match the selected view.");}
    }
    record Actor(int id, String name, String email, String role) {}
    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status, String message) { super(message); this.status=status; }
    }
    static String text(JsonNode n,String key) { return n.path(key).asText(""); }
    static String required(JsonNode n,String key) {
        String s=text(n,key); if(s.isBlank() || s.length()>4096) throw new Failure(400,"Provide a valid "+key+"."); return s;
    }
    static int integer(JsonNode n,String key,int fallback) {
        String s=text(n,key); if(s.isBlank()) return fallback;
        try { return Integer.parseInt(s); } catch(NumberFormatException e) {throw new Failure(400,key+" must be an integer.");}
    }
    static int id(JsonNode n,String key) {int i=integer(n,key,0); if(i<1)throw new Failure(400,key+" must be positive."); return i;}
    static BigDecimal money(JsonNode n,String key,boolean optional) {
        String s=text(n,key); if(optional&&s.isBlank())return null;
        try{return new BigDecimal(s);}catch(NumberFormatException e){throw new Failure(400,key+" must be a decimal.");}
    }
    static double number(JsonNode n,String key) {
        try {double d=Double.parseDouble(required(n,key)); if(!Double.isFinite(d))throw new NumberFormatException(); return d;}
        catch(NumberFormatException e){throw new Failure(400,key+" must be finite.");}
    }
    static LocalDate date(JsonNode n,String key,boolean optional) {String s=text(n,key);return optional&&s.isBlank()?null:LocalDate.parse(s);}
    static List<Integer> ids(JsonNode n,String key) {
        List<Integer> list=new ArrayList<>(); JsonNode value=n.path(key);
        if(value.isArray()) {for(JsonNode v:value)list.add(Integer.parseInt(v.asText()));}
        else if(!value.asText("").isBlank()) {for(String s:value.asText().split(","))list.add(Integer.parseInt(s.trim()));}
        if(list.size()>200)throw new Failure(400,"Too many identifiers.");return list;
    }
    static Actor role(Actor actor,String role) {
        if(actor==null)throw new Failure(401,"Sign in to continue.");
        if(role!=null&&!role.equals(actor.role()))throw new Failure(403,"This action requires a "+role.toLowerCase()+" account.");
        return actor;
    }
    static Map<String,Object> ok(Object result) {return Map.of("result",result==null?true:result);}
}
