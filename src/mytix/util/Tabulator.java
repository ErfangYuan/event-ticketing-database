package mytix.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Tabulator {

    public static final int ASSUMED_TERMINAL_WIDTH = 100;
    public static final int MAX_TABLE_WIDTH = 96;
    public static final int MAX_CELL_WIDTH = 28;

    private Tabulator() {}

    public static void printTitle(String title) {
        int inner = Math.min(MAX_TABLE_WIDTH, ASSUMED_TERMINAL_WIDTH - 4);
        String bar = "=".repeat(inner);
        System.out.println(bar);
        String t = title == null ? "" : title.trim();
        if (t.length() > inner) {
            t = t.substring(0, inner - 3) + "...";
        }
        int pad = Math.max(0, (inner - t.length()) / 2);
        System.out.println(" ".repeat(pad) + t);
        System.out.println(bar);
    }

    public static void printTable(String title, List<String> headers, List<List<String>> rows) {
        printTitle(title);
        if (headers == null || headers.isEmpty()) {
            System.out.println("(no columns)");
            return;
        }
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = Math.min(MAX_CELL_WIDTH, Math.max(1, cellLen(headers.get(c))));
        }
        if (rows != null) {
            for (List<String> row : rows) {
                for (int c = 0; c < cols; c++) {
                    String v = c < row.size() ? row.get(c) : "";
                    widths[c] = Math.min(MAX_CELL_WIDTH, Math.max(widths[c], cellLen(v)));
                }
            }
        }

        
        int sep = cols + 1; 
        int contentBudget = MAX_TABLE_WIDTH - sep;
        int sum = Arrays.stream(widths).sum();
        if (sum > contentBudget && sum > 0) {
            double scale = (double) contentBudget / sum;
            int allocated = 0;
            for (int c = 0; c < cols - 1; c++) {
                widths[c] = Math.max(1, (int) Math.floor(widths[c] * scale));
                allocated += widths[c];
            }
            widths[cols - 1] = Math.max(1, contentBudget - allocated);
        }

        String sepLine = buildSep(widths);
        System.out.println(sepLine);
        System.out.println(formatRow(headers, widths));
        System.out.println(sepLine);
        if (rows == null || rows.isEmpty()) {
            List<String> empty = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                empty.add(c == 0 ? "(empty)" : "");
            }
            System.out.println(formatRow(empty, widths));
        } else {
            for (List<String> row : rows) {
                System.out.println(formatRow(padRow(row, cols), widths));
            }
        }
        System.out.println(sepLine);
    }

    /**
     * Prints a table and returns false when there are no selectable rows, so the caller
     * can abort before prompting for IDs.
     */
    public static boolean printTableRequireRows(
            String title, List<String> headers, List<List<String>> rows, String emptyMessage) {
        printTable(title, headers, rows);
        if (rows == null || rows.isEmpty()) {
            System.out.println(
                    emptyMessage == null || emptyMessage.isBlank()
                            ? "Nothing available for this operation."
                            : emptyMessage);
            return false;
        }
        return true;
    }

    private static List<String> padRow(List<String> row, int cols) {
        List<String> out = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            out.add(c < row.size() && row.get(c) != null ? row.get(c) : "");
        }
        return out;
    }

    private static int cellLen(String s) {
        return s == null ? 0 : s.length();
    }

    private static String clip(String s, int width) {
        if (s == null) {
            s = "";
        }
        if (s.length() <= width) {
            return s;
        }
        if (width <= 3) {
            return s.substring(0, width);
        }
        return s.substring(0, width - 3) + "...";
    }

    private static String formatRow(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < widths.length; c++) {
            String v = clip(c < cells.size() ? cells.get(c) : "", widths[c]);
            sb.append(String.format(" %-" + widths[c] + "s |", v));
        }
        return sb.toString();
    }

    private static String buildSep(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        return sb.toString();
    }
}
