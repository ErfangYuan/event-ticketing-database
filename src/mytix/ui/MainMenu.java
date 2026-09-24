package mytix.ui;

import mytix.AppContext;
import mytix.database.DatabaseLogistics;
import mytix.ui.account.CreateAccountUI;
import mytix.ui.account.DeleteAccountUI;
import mytix.ui.customer.CustomerMenu;
import mytix.ui.organizer.OrganizerMenu;
import mytix.ui.query.QueryMenu;
import mytix.ui.report.ReportMenu;
import mytix.util.ConsoleIO;

public final class MainMenu {

    private final ConsoleIO io;
    private final AppContext ctx;

    public MainMenu(ConsoleIO io, AppContext ctx) {
        this.io = io;
        this.ctx = ctx;
    }

    public void loop() {
        while (true) {
            System.out.println();
            System.out.println("================================================");
            System.out.println("                 MyTix TUI");
            System.out.println("================================================");
            System.out.println(DatabaseLogistics.statusMessage());
            System.out.println("  1. Create Account");
            System.out.println("  2. Delete Account");
            System.out.println("  3. Customer Menu");
            System.out.println("  4. Organizer Menu");
            System.out.println("  5. Queries (Q1–Q7)");
            System.out.println("  6. Reports (R1–R9)");
            System.out.println("  q. Quit");
            String choice = io.prompt("Select: ");
            if (io.reachedEof()) {
                System.out.println("Goodbye.");
                return;
            }
            switch (choice) {
                case "1" -> new CreateAccountUI(ctx).run(io);
                case "2" -> new DeleteAccountUI(ctx).run(io);
                case "3" -> new CustomerMenu(io, ctx).loop();
                case "4" -> new OrganizerMenu(io, ctx).loop();
                case "5" -> new QueryMenu(io, ctx).loop();
                case "6" -> new ReportMenu(io, ctx).loop();
                case "q", "Q" -> {
                    System.out.println("Goodbye.");
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }
}
