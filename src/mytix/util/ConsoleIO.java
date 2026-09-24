package mytix.util;

import java.util.List;
import java.util.Scanner;

public final class ConsoleIO {

    private final Scanner in;
    private boolean eof;

    public ConsoleIO(Scanner in) {
        this.in = in;
    }

    public boolean reachedEof() {
        return eof;
    }

    public String prompt(String label) {
        System.out.print(label);
        System.out.flush();
        if (!in.hasNextLine()) {
            eof = true;
            return "";
        }
        return in.nextLine().trim();
    }

    public String promptNonEmpty(String label) {
        while (true) {
            if (eof) {
                return "";
            }
            String v = prompt(label);
            if (eof) {
                return "";
            }
            if (!v.isEmpty()) {
                return v;
            }
            System.out.println("Please enter a non-empty value.");
        }
    }

    public int promptInt(String label, int defaultValue) {
        String raw = prompt(label + " [" + defaultValue + "]: ");
        if (eof || raw.isEmpty()) {
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
        int fallback = blankUsesDefaultIndex >= 1 && blankUsesDefaultIndex <= labels.size()
                ? blankUsesDefaultIndex - 1
                : 0;
        while (true) {
            if (eof) {
                return values.get(fallback);
            }
            String raw = prompt("Select 1-" + labels.size() + defaultSuffix + ": ");
            if (eof) {
                return values.get(fallback);
            }
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
            if (eof) {
                return !defaultNo;
            }
            String raw = prompt(label + " (" + hint + "): ").trim();
            if (eof || raw.isEmpty()) {
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
        if (eof) {
            return;
        }
        prompt("Press Enter to continue...");
    }

    public void placeholder(String feature) {
        System.out.println();
        System.out.println("[PLACEHOLDER] " + feature);
        System.out.println("Backend SQL will be wired in Stage 4–5. Showing sample empty/demo table.");
        System.out.println();
    }
}
