package mytix.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import mytix.database.DatabaseLogistics;
import mytix.database.demo.DemoDataService;
import mytix.database.user.*;
import static mytix.api.ApiData.*;

/** Loopback-only project API. Next.js is the sole browser-facing session/cookie boundary. */
public final class WebServer {
    static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(30).maxStringLength(12000).maxNumberLength(50).build()).build())
        .registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private record Session(int userID,Instant expires) {}
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final ReadWriteLock gate=new ReentrantReadWriteLock(true);
    private final byte[] secret;
    private final SchemaExplorer explorer;
    private final WorkflowApi workflows=new WorkflowApi();
    private final JdbcUserRepository users=new JdbcUserRepository();
    private final SecureRandom random=new SecureRandom();
    private final boolean demoEnabled;
    private long authWindow=0;private int authCount=0;
    WebServer(String secret)throws SQLException {
        if(secret==null||secret.length()<32)throw new IllegalArgumentException("Set a random MYTIX_API_SECRET of at least 32 characters in both services.");
        this.secret=secret.getBytes(StandardCharsets.UTF_8);this.demoEnabled="true".equals(System.getenv("MYTIX_DEMO_MODE"));explorer=new SchemaExplorer();
    }
    public static void main(String[] args)throws Exception {
        int port=Integer.parseInt(System.getenv().getOrDefault("MYTIX_API_PORT","8081"));
        WebServer app=new WebServer(System.getenv("MYTIX_API_SECRET"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),64);
        ThreadPoolExecutor pool=new ThreadPoolExecutor(4,12,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(pool);server.createContext("/api/",app::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);pool.shutdown();}));server.start();
        System.out.println("MyTix API ready at http://127.0.0.1:"+port+" (UTC; demo maintenance "+(app.demoEnabled?"enabled":"disabled")+")");
    }
    private void handle(HttpExchange x) {
        try {
            String supplied=x.getRequestHeaders().getFirst("X-Mytix-Key");
            if(supplied==null||!MessageDigest.isEqual(secret,supplied.getBytes(StandardCharsets.UTF_8)))throw new Failure(403,"Service access denied.");
            if(!x.getRequestMethod().equals("POST"))throw new Failure(405,"Use POST with a JSON object.");
            if(!Optional.ofNullable(x.getRequestHeaders().getFirst("Content-Type")).orElse("").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new Failure(415,"Use application/json.");
            byte[] raw=x.getRequestBody().readNBytes(65537);if(raw.length>65536)throw new Failure(413,"Request is too large.");
            JsonNode body=JSON.readTree(raw);if(body==null||!body.isObject())throw new Failure(400,"Expected a JSON object.");
            String path=x.getRequestURI().getPath().substring(5);
            boolean read=path.equals("status")||path.equals("schema")||path.equals("table")||path.equals("auth/me")||path.startsWith("query/")||path.startsWith("report/")||path.startsWith("catalog/");
            Lock lock=read?gate.readLock():gate.writeLock();
            if(!lock.tryLock(45,TimeUnit.SECONDS))throw new Failure(503,"Demo maintenance is busy. Try again shortly.");
            try {Actor actor=actor(x.getRequestHeaders().getFirst("X-Mytix-Session"));send(x,200,route(path,body,actor,x));}
            finally {lock.unlock();}
        }catch(Failure e){send(x,e.status,Map.of("error",e.getMessage()));}
        catch(com.fasterxml.jackson.core.JacksonException|IllegalArgumentException|java.time.DateTimeException e){send(x,400,Map.of("error","Check the field values, dates and JSON structure."));}
        catch(Exception e){
            boolean sql=false;for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof SQLException)sql=true;
            String message=sql?"The change conflicts with stored data or a required relationship. Check the selected records.":e instanceof IllegalStateException?e.getMessage():"The request could not be completed. Try again.";
            send(x,sql?409:422,Map.of("error",message==null?"Request failed.":message));
        }finally{x.close();}
    }
    private Object route(String path,JsonNode b,Actor actor,HttpExchange x)throws Exception {
        if(path.matches("query/q[1-7]"))return workflows.query(Integer.parseInt(path.substring(7)),b);
        if(path.matches("report/r[1-9]"))return workflows.report(Integer.parseInt(path.substring(8)),b);
        if(path.startsWith("catalog/"))return workflows.catalog(path.substring(8),b,actor);
        if(path.startsWith("customer/")||path.startsWith("organizer/"))return workflows.action(path,b,actor);
        return switch(path) {
            case "status" -> Map.of("database",explorer.status(),"demoEnabled",demoEnabled);
            case "schema" -> explorer.schema();
            case "table" -> explorer.page(b,actor);
            case "auth/me" -> actor==null?Map.of("authenticated",false):Map.of("authenticated",true,"user",actor);
            case "auth/login" -> {
                throttleAuth();String password=required(b,"password");if(password.length()>1024)throw new Failure(400,"Password is too long.");
                UserRecord u=users.authenticate(required(b,"email"),password,null).orElseThrow(()->new Failure(401,"Email or password is incorrect."));
                sessions.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));
                if(sessions.size()>=2048)throw new Failure(429,"Session capacity reached. Try later.");
                byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                sessions.put(token,new Session(u.userID(),Instant.now().plusSeconds(21600)));
                yield Map.of("sessionToken",token,"user",new Actor(u.userID(),u.name(),u.email(),u.userType()));
            }
            case "auth/logout" -> {String token=x.getRequestHeaders().getFirst("X-Mytix-Session");if(token!=null)sessions.remove(token);yield ok(true);}
            case "auth/register" -> {
                throttleAuth();String password=required(b,"password");if(password.length()<10)throw new Failure(400,"Use at least 10 characters for a Web password.");
                String email=required(b,"email");if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new Failure(400,"Provide a valid email.");
                // A generated local payment alias prevents shared fake-card identifiers crossing accounts.
                String card="WEB-"+UUID.randomUUID().toString().replace("-","").substring(0,24);
                yield ok(users.createAccount(new CreateUserRequest(email,password,required(b,"name"),required(b,"address"),date(b,"birthday",false),required(b,"role"),card,text(b,"cardExpiry"))));
            }
            case "auth/delete" -> {role(actor,null);boolean deleted=users.deleteAccount(actor.email(),required(b,"password"));if(!deleted)throw new Failure(409,"Account could not be deleted. Check your password and active obligations.");sessions.entrySet().removeIf(e->e.getValue().userID()==actor.id());yield ok(true);}
            case "reference/create" -> explorer.reference(b,actor,false);
            case "reference/delete" -> explorer.reference(b,actor,true);
            case "demo/load","demo/clear" -> {
                if(!demoEnabled)throw new Failure(403,"Demo maintenance is disabled for this installation.");
                // Intentionally public in the isolated demo. The BFF still requires same-origin JSON.
                DemoDataService.Result result=path.endsWith("load")?new DemoDataService().resetAndLoad():new DemoDataService().clear();
                sessions.clear();yield result;
            }
            default -> throw new Failure(404,"Unknown MyTix endpoint.");
        };
    }
    private Actor actor(String token)throws SQLException {
        if(token==null)return null;Session session=sessions.get(token);if(session==null)return null;
        if(session.expires().isBefore(Instant.now())){sessions.remove(token);return null;}
        try(Connection c=DatabaseLogistics.getConnection();PreparedStatement ps=c.prepareStatement("SELECT userID,name,email,userType FROM users WHERE userID=? AND deletedAt IS NULL")){
            ps.setInt(1,session.userID());try(ResultSet r=ps.executeQuery()){if(r.next())return new Actor(r.getInt(1),r.getString(2),r.getString(3),r.getString(4));}
        }sessions.remove(token);return null;
    }
    private synchronized void throttleAuth(){long now=System.currentTimeMillis()/60000;if(now!=authWindow){authWindow=now;authCount=0;}if(++authCount>60)throw new Failure(429,"Too many sign-in attempts. Try again in one minute.");}
    private static void send(HttpExchange x,int status,Object value) {
        try {byte[] data=JSON.writeValueAsBytes(value);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);}catch(Exception ignored){/* Client may have disconnected. */}
    }
}
