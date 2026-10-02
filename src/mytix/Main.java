package mytix;

import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import mytix.database.DatabaseLogistics;
import mytix.ui.MainMenu;
import mytix.util.ConsoleIO;

public final class Main {

    public static void main(String[] args) {
        java.io.Console console = ConsoleIO.interactiveConsole();
        var outputCharset = console == null ? StandardCharsets.UTF_8 : console.charset();
        System.setOut(new PrintStream(System.out, true, outputCharset));
        System.setErr(new PrintStream(System.err, true, outputCharset));
        System.out.println("MyTix");
        System.out.println(DatabaseLogistics.statusMessage());
        AppContext ctx = new AppContext();
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            ConsoleIO io = new ConsoleIO(scanner, console);
            new MainMenu(io, ctx).loop();
        } catch (ConsoleIO.InputClosedException e) {
            System.out.println("Input closed. Goodbye.");
        }
    }
}
