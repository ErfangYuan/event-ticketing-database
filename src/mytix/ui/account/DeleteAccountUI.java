package mytix.ui.account;

import mytix.AppContext;
import mytix.database.DatabaseLogistics;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;

public final class DeleteAccountUI implements MenuAction {

    private final AppContext ctx;

    public DeleteAccountUI(AppContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String label() {
        return "Delete Account";
    }

    @Override
    public void run(ConsoleIO io) {
        if (!DatabaseLogistics.isConfigured()) {
            System.out.println("Database offline — cannot delete account.");
            io.pause();
            return;
        }
        String email = io.promptNonEmpty("Email: ");
        String password = io.promptNonEmpty("Password: ");
        String confirm = io.prompt("Type YES to confirm deletion: ");
        if (!confirm.equalsIgnoreCase("YES")) {
            System.out.println("Deletion cancelled.");
            io.pause();
            return;
        }
        try {
            boolean deleted = ctx.users().deleteAccount(email, password);
            if (deleted) {
                if (ctx.session().isLoggedIn() && email.equalsIgnoreCase(ctx.session().getEmail())) {
                    ctx.session().logout();
                }
                System.out.println("Account deleted: " + email);
            } else {
                System.out.println("Delete failed: invalid email/password.");
            }
        } catch (RuntimeException e) {
            System.out.println("Delete refused: " + e.getMessage());
        }
        io.pause();
    }
}
