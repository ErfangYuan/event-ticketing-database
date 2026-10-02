package mytix.database.demo;

import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.util.Arrays;
import mytix.config.DatabaseConfig;

/** Command-line entrypoint; naming the target database is the destructive-operation acknowledgement. */
public final class DemoDataMain {
    private DemoDataMain() {}

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            usage(); return;
        }
        if (args.length != 2 || !Arrays.asList("--reset-and-load", "--clear").contains(args[0])
                || !DatabaseConfig.DATABASE.equals(args[1])) {
            usage(); System.exit(2); return;
        }
        try {
            System.out.println("Project database: " + DatabaseConfig.DATABASE + " (" + DatabaseConfig.HOST + ":" + DatabaseConfig.PORT + ")");
            System.out.println(args[0].equals("--clear") ? "Clearing project data atomically..."
                    : "Preparing synthetic credentials and NLP projections, then replacing project data atomically...");
            DemoDataService service = new DemoDataService();
            DemoDataService.Result result = args[0].equals("--clear") ? service.clear() : service.resetAndLoad();
            System.out.println("Completed; UTC anchor date: " + result.anchorDate());
            result.counts().forEach((table, count) -> System.out.println(table + ": " + count));
            result.scenarios().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> System.out.println("scenario." + entry.getKey() + ": " + entry.getValue()));
            if (result.demoPassword() != null) {
                System.out.println("Synthetic demo customer: " + result.customerEmail());
                System.out.println("Synthetic demo organizer: " + result.organizerEmail());
                System.out.println("Public demo password: " + result.demoPassword());
            }
        } catch (RuntimeException error) {
            System.err.println("Demo operation failed: " + error.getMessage());
            System.exit(1);
        }
    }
    private static void usage() {
        System.out.println("Usage: DemoDataMain --reset-and-load <configured-database> | --clear <configured-database>");
        System.out.println("Both operations replace/remove ALL data in the 22 project tables. Stop other application writers first.");
        System.out.println("Set MYTIX_DB_* for the target; install sql/schema.sql and NLP models first. See docs/demo-data.md.");
    }
}
