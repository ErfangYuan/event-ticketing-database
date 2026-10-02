package mytix.ui.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mytix.AppContext;
import mytix.database.query.Q5Filter;
import mytix.database.query.GeographicFilter;
import mytix.ui.AbstractMenu;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;
import mytix.util.Tabulator;

public final class QueryMenu extends AbstractMenu {

    private final AppContext ctx;

    
    public QueryMenu(ConsoleIO io, AppContext ctx) {
        super(io, "Queries (Q1–Q7)", "8", "Back to main menu");
        this.ctx = ctx;
    }

    @Override
    protected Map<String, MenuAction> actions() {
        Map<String, MenuAction> m = new LinkedHashMap<>();
        m.put("1", q("Q1 Vicinity search", this::q1));
        m.put("2", q("Q2 Postal / adjacent", this::q2));
        m.put("3", q("Q3 Exact address", this::q3));
        m.put("4", q("Q4 Temporal + availability", this::q4));
        m.put("5", q("Q5 Combined filters", this::q5));
        m.put("6", q("Q6 Seat-map summary", this::q6));
        m.put("7", q("Q7 Best consecutive seats", this::q7));
        return m;
    }

    private void q1() {
        try {
            Double lat = promptDouble("latitude");
            Double lng = promptDouble("longitude");
            if (lat == null || lng == null) {
                io.pause();
                return;
            }
            int radiusKm = io.promptInt("distance km", 15);
            String rankBy = io.choose(
                    "Rank results by:",
                    List.of("Distance", "Ascending price", "Descending price"),
                    List.of("distance", "price_asc", "price_desc"),
                    1);
            List<List<String>> rows = ctx.queries().q1Vicinity(lat, lng, radiusKm, rankBy);
            Tabulator.printTable(
                    "Q1 Results",
                    List.of("performanceID", "event", "venue", "city", "distanceKm", "cheapest"),
                    rows);
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private void q2() {
        try {
            String postalCode = io.promptNonEmpty("postalCode: ");
            List<List<String>> rows = ctx.queries().q2PostalAdjacent(postalCode);
            Tabulator.printTable(
                    "Q2 Results",
                    List.of("performanceID", "event", "venue", "city", "postalCode"),
                    rows);
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private void q3() {
        try {
            String address = io.promptNonEmpty("address: ");
            List<List<String>> rows = ctx.queries().q3ExactAddress(address);
            Tabulator.printTable(
                    "Q3 Venue + Upcoming Performances",
                    List.of("venueID", "venueName", "address", "performanceID", "title", "date", "startTime"),
                    rows);
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private void q4() {
        try {
            LocalDate from = promptDate("dateFrom (YYYY-MM-DD)");
            LocalDate to = promptDate("dateTo (YYYY-MM-DD)");
            if (from == null || to == null) {
                io.pause();
                return;
            }
            int minAvailable = io.promptInt("minAvailableTickets", 1);
            GeographicFilter geography = promptGeography();
            if (geography == null) {
                io.pause();
                return;
            }
            List<String> headers =
                    List.of("performanceID", "title", "venue", "city", "date", "startTime", "available");
            List<List<String>> rows = ctx.queries().q4TemporalAvailability(from, to, minAvailable, geography);
            Tabulator.printTable("Q4 Results", headers, rows);
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private GeographicFilter promptGeography() {
        String mode = io.choose("Geographic refinement:",
                List.of("All locations", "Vicinity", "Postal / adjacent", "Exact address"),
                List.of("ALL", "VICINITY", "POSTAL", "ADDRESS"), 1);
        if (mode.equals("POSTAL")) {
            return GeographicFilter.postal(io.promptNonEmpty("postalCode: "));
        }
        if (mode.equals("ADDRESS")) {
            return GeographicFilter.address(io.promptNonEmpty("address: "));
        }
        if (mode.equals("VICINITY")) {
            Double lat = promptDouble("latitude");
            Double lng = promptDouble("longitude");
            if (lat == null || lng == null) {
                return null;
            }
            int radius = io.promptInt("distance km", 15);
            String rank = io.choose("Rank results by:",
                    List.of("Distance", "Ascending price", "Descending price"),
                    List.of("distance", "price_asc", "price_desc"), 1);
            return GeographicFilter.vicinity(lat, lng, radius, rank);
        }
        return GeographicFilter.all();
    }

    private void q5() {
        try {
            String city = blankToNull(io.prompt("city (optional): "));
            String segment = blankToNull(io.prompt("segment (optional): "));
            String genre = blankToNull(io.prompt("genre (optional): "));
            LocalDate dateFrom = promptOptionalDate("dateFrom (optional, YYYY-MM-DD): ");
            LocalDate dateTo = promptOptionalDate("dateTo (optional, YYYY-MM-DD): ");
            BigDecimal minPrice = promptOptionalPrice("minPrice (optional): ");
            BigDecimal maxPrice = promptOptionalPrice("maxPrice (optional): ");
            Integer minAvailable = promptOptionalInt("minAvailable (optional): ");
            String sectionType = io.choose(
                    "Section type:",
                    List.of("Any", "Reserved seating", "General admission"),
                    Arrays.asList(null, "RESERVED", "GA"),
                    1);

            Q5Filter filter = new Q5Filter(
                    city, segment, genre, dateFrom, dateTo, minPrice, maxPrice, minAvailable, sectionType);
            List<List<String>> rows = ctx.queries().q5Combined(filter);
            Tabulator.printTable(
                    "Q5 Results",
                    List.of("performanceID", "event", "genre", "city", "date", "available", "cheapest"),
                    rows);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date (expected YYYY-MM-DD): " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private void q6() {
        try {
            Integer performanceID = promptInt("performanceID");
            if (performanceID == null) {
                io.pause();
                return;
            }
            List<List<String>> rows = ctx.queries().q6SeatMapSummary(performanceID);
            Tabulator.printTable(
                    "Q6 Seat-map Summary",
                    List.of("section", "tier", "price", "available", "sold", "blocked"),
                    rows);
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    private void q7() {
        try {
            Integer performanceID = promptInt("performanceID");
            if (performanceID == null) {
                io.pause();
                return;
            }
            int quantity = io.promptInt("q (consecutive seats)", 2);
            BigDecimal budget = promptOptionalPrice("budget (optional, blank=none): ");
            List<List<String>> rows = ctx.queries().q7BestConsecutive(performanceID, quantity, budget);
            Tabulator.printTable(
                    "Q7 Best Available",
                    List.of("section", "row", "fromSeat", "toSeat", "totalPrice"),
                    rows);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
        io.pause();
    }

    

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Integer promptInt(String label) {
        String raw = io.promptNonEmpty(label + ": ");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid integer.");
            return null;
        }
    }

    private Double promptDouble(String label) {
        String raw = io.promptNonEmpty(label + ": ");
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
            return null;
        }
    }

    private LocalDate promptDate(String label) {
        String raw = io.promptNonEmpty(label + ": ");
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date (expected YYYY-MM-DD).");
            return null;
        }
    }

    private LocalDate promptOptionalDate(String label) {
        String raw = io.prompt(label);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw.trim());
    }

    private Integer promptOptionalInt(String label) {
        String raw = io.prompt(label);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.parseInt(raw.trim());
    }

    private BigDecimal promptOptionalPrice(String label) {
        String raw = io.prompt(label);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim());
    }

    private static MenuAction q(String label, Runnable r) {
        return new MenuAction() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public void run(ConsoleIO io) {
                r.run();
            }
        };
    }
}
