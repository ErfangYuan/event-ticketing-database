package mytix.util;

import java.util.List;
import java.util.Arrays;
import java.util.Scanner;

public final class ConsoleIO {

    private final Scanner in;
    private final java.io.Console console;
    private boolean eof;

    public ConsoleIO(Scanner in) {
        this(in, null);
    }

    public ConsoleIO(Scanner in, java.io.Console console) {
        this.in = in;
        this.console = console;
    }

    public static java.io.Console interactiveConsole() {
        java.io.Console console = System.console();
        if (console == null) {
            return null;
        }
        try {
            // JDK 22+ may return a Console even for pipes; keep those on the single Scanner reader.
            return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(console) ? console : null;
        } catch (NoSuchMethodException e) {
            return console; // On JDK 17, a Console exists only for an interactive terminal.
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public boolean reachedEof() {
        return eof;
    }

    /** Ends the active input workflow without substituting an empty value or a default. */
    public static final class InputClosedException extends RuntimeException {
        private InputClosedException() {
            super("Input closed; incomplete command cancelled.");
        }
    }

    public static void rethrowIfInputClosed(RuntimeException exception) {
        if (exception instanceof InputClosedException) {
            throw exception;
        }
    }

    public String prompt(String label) {
        return readLine(label).trim();
    }

    private String readLine(String label) {
        if (eof) {
            throw new InputClosedException();
        }
        String value;
        if (console != null) {
            value = console.readLine("%s", label);
        } else {
            System.out.print(label);
            System.out.flush();
            value = in.hasNextLine() ? in.nextLine() : null;
        }
        if (value == null) {
            eof = true;
            throw new InputClosedException();
        }
        return value;
    }

    public String promptNonEmpty(String label) {
        while (true) {
            String v = prompt(label);
            if (!v.isEmpty()) {
                return v;
            }
            System.out.println("Please enter a non-empty value.");
        }
    }

    public String promptPassword(String label) {
        while (true) {
            if (eof) {
                throw new InputClosedException();
            }
            String value;
            if (console == null) {
                value = readLine(label);
            } else {
                char[] chars = console.readPassword("%s", label);
                if (chars == null) {
                    eof = true;
                    throw new InputClosedException();
                }
                try {
                    value = new String(chars);
                } finally {
                    Arrays.fill(chars, '\0');
                }
            }
            if (!value.isBlank() && value.length() <= PasswordUtil.MAX_PASSWORD_LENGTH) {
                return value;
            }
            System.out.println("Password must contain 1 to 1024 characters and not be blank.");
        }
    }

    public int promptInt(String label, int defaultValue) {
        String raw = prompt(label + " [" + defaultValue + "]: ");
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            System.out.println("Invalid integer; using default " + defaultValue + ".");
            return defaultValue;
        }
    }

    public String choose(String title, List<String> labels, List<String> values, int blankUsesDefaultIndex) {
        if (labels.size() != values.size() || labels.isEmpty()) {
            throw new IllegalArgumentException("labels and values must be same non-empty size");
        }
        System.out.println(title);
        for (int i = 0; i < labels.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + labels.get(i));
        }
        String defaultSuffix = blankUsesDefaultIndex >= 1 && blankUsesDefaultIndex <= labels.size()
                ? " [" + blankUsesDefaultIndex + "]"
                : "";
        while (true) {
            String raw = prompt("Select 1-" + labels.size() + defaultSuffix + ": ");
            if (raw.isEmpty() && blankUsesDefaultIndex >= 1 && blankUsesDefaultIndex <= labels.size()) {
                return values.get(blankUsesDefaultIndex - 1);
            }
            try {
                int idx = Integer.parseInt(raw);
                if (idx >= 1 && idx <= labels.size()) {
                    return values.get(idx - 1);
                }
            } catch (NumberFormatException ignored) {
                
            }
            System.out.println("Invalid choice. Enter a number from 1 to " + labels.size() + ".");
        }
    }

    public String chooseFromTable(String title, List<List<String>> rows, int valueColumnIndex) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rows must be non-empty");
        }
        List<String> labels = new java.util.ArrayList<>(rows.size());
        List<String> values = new java.util.ArrayList<>(rows.size());
        for (List<String> row : rows) {
            values.add(row.get(valueColumnIndex));
            StringBuilder lb = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    lb.append(" | ");
                }
                lb.append(row.get(i) == null ? "" : row.get(i));
            }
            labels.add(lb.toString());
        }
        return choose(title, labels, values, 0);
    }

    public int chooseIntFromTable(String title, List<List<String>> rows, int valueColumnIndex) {
        return Integer.parseInt(chooseFromTable(title, rows, valueColumnIndex));
    }

    public boolean confirmYesNo(String label, boolean defaultNo) {
        String hint = defaultNo ? "Y/N [N]" : "Y/N [Y]";
        while (true) {
            String raw = prompt(label + " (" + hint + "): ").trim();
            if (raw.isEmpty()) {
                return !defaultNo;
            }
            if (raw.equalsIgnoreCase("Y") || raw.equalsIgnoreCase("YES")) {
                return true;
            }
            if (raw.equalsIgnoreCase("N") || raw.equalsIgnoreCase("NO")) {
                return false;
            }
            System.out.println("Please answer Y or N.");
        }
    }

    public void pause() {
        prompt("Press Enter to continue...");
    }

    public void placeholder(String feature) {
        System.out.println();
        System.out.println("[PLACEHOLDER] " + feature);
        System.out.println("Backend SQL will be wired in Stage 4–5. Showing sample empty/demo table.");
        System.out.println();
    }
}
