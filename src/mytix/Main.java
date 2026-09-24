package mytix;

import java.util.Scanner;
import mytix.database.DatabaseLogistics;
import mytix.ui.MainMenu;
import mytix.util.ConsoleIO;

public final class Main {

    public static void main(String[] args) {
        System.out.println("MyTix");
        System.out.println(DatabaseLogistics.statusMessage());
        AppContext ctx = new AppContext();
        try (Scanner scanner = new Scanner(System.in)) {
            ConsoleIO io = new ConsoleIO(scanner);
            new MainMenu(io, ctx).loop();
        }
    }
}
