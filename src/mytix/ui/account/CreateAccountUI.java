package mytix.ui.account;

import java.time.LocalDate;
import java.util.List;
import mytix.AppContext;
import mytix.database.DatabaseLogistics;
import mytix.database.user.CreateUserRequest;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;
import mytix.util.Tabulator;

public final class CreateAccountUI implements MenuAction {

    private final AppContext ctx;

    public CreateAccountUI(AppContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String label() {
        return "Create Account";
    }

    @Override
    public void run(ConsoleIO io) {
        if (!DatabaseLogistics.isConfigured()) {
            System.out.println("Database offline — cannot create account.");
            io.pause();
            return;
        }
        String role = io.choose(
                "Account type:",
                List.of("Customer", "Organizer"),
                List.of("CUSTOMER", "ORGANIZER"),
                1);
        if (!role.equals("CUSTOMER") && !role.equals("ORGANIZER")) {
            System.out.println("Invalid role.");
            io.pause();
            return;
        }
        String name = io.promptNonEmpty("Name: ");
        String email = io.promptNonEmpty("Email: ");
        String password = io.promptNonEmpty("Password: ");
        String address = io.promptNonEmpty("Address: ");
        LocalDate birthday;
        try {
            birthday = LocalDate.parse(io.promptNonEmpty("Birthday (YYYY-MM-DD): "));
        } catch (RuntimeException e) {
            System.out.println("Invalid date format.");
            io.pause();
            return;
        }
        String cardNumber = null;
        String cardExpiry = null;
        if (role.equals("CUSTOMER")) {
            cardNumber = io.promptNonEmpty("Credit card number: ");
            cardExpiry = io.promptNonEmpty("Card expiry (YYYY-MM): ");
        }

        try {
            int userID = ctx.users().createAccount(new CreateUserRequest(
                    email, password, name, address, birthday, role, cardNumber, cardExpiry));
            Tabulator.printTable(
                    "Create Account — Success",
                    List.of("Field", "Value"),
                    List.of(
                            List.of("userID", String.valueOf(userID)),
                            List.of("role", role),
                            List.of("name", name),
                            List.of("email", email)));
        } catch (RuntimeException e) {
            System.out.println("Create account failed: " + e.getMessage());
        }
        io.pause();
    }
}
