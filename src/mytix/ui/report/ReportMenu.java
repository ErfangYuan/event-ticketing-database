package mytix.ui.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mytix.AppContext;
import mytix.ui.AbstractMenu;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;
import mytix.util.Tabulator;

public final class ReportMenu extends AbstractMenu {

    private final AppContext ctx;

    public ReportMenu(ConsoleIO io, AppContext ctx) {
        super(io, "Reports (R1–R9)", "0", "Back to main menu");
        this.ctx = ctx;
    }

    @Override
    protected Map<String, MenuAction> actions() {
        Map<String, MenuAction> m = new LinkedHashMap<>();
        m.put("1", r("R1 Revenue by city / venue", this::r1));
        m.put("2", r("R2 Events & performances by taxonomy×geo", this::r2));
        m.put("3", r("R3 Organizer revenue rank", this::r3));
        m.put("4", r("R4 Scalper candidates", this::r4));
        m.put("5", r("R5 Customer order ranks", this::r5));
        m.put("6", r("R6 Top cancellations", this::r6));
        m.put("7", r("R7 Sell-through", this::r7));
        m.put("8", r("R8 Resale report", this::r8));
        m.put("9", r("R9 Noun phrases per event", this::r9));
        return m;
    }

    private void r1() {
        String grain = io.choose(
                "R1 mode:",
                List.of("By city", "By venue within a city"),
                List.of("city", "venue"),
                1);
        String dateFrom = io.promptNonEmpty("dateFrom (YYYY-MM-DD): ");
        String dateTo = io.promptNonEmpty("dateTo (YYYY-MM-DD): ");
        if ("venue".equals(grain)) {
            String city = io.promptNonEmpty("city: ");
            List<List<String>> venues;
            try {
                venues = ctx.catalog().listVenuesByCity(city);
            } catch (RuntimeException e) {
                System.out.println("Could not list venues: " + e.getMessage());
                io.pause();
                return;
            }
            if (!Tabulator.printTableRequireRows(
                    "Venues in " + city,
                    List.of("venueID", "venueName", "city", "postalCode"),
                    venues,
                    "No venues found in city: " + city)) {
                io.pause();
                return;
            }
            String venueID = io.promptNonEmpty("venueID: ");
            runAndPrint(
                    1,
                    "R1 Revenue for selected venue",
                    List.of(grain, dateFrom, dateTo, city, venueID));
        } else {
            String city = io.prompt("city (optional, blank=all cities): ");
            runAndPrint(1, "R1 Revenue by city", List.of(grain, dateFrom, dateTo, city));
        }
    }

    private void r2() {
        String grain = io.choose(
                "R2 grain:",
                List.of("Country", "Country and city", "Country, city and venue"),
                List.of("country", "city", "venue"),
                1);
        runAndPrint(2, "R2 Events & performances by taxonomy x geo", List.of(grain));
    }

    private void r3() {
        String scope = io.choose(
                "R3 scope:",
                List.of("Overall", "Per country", "Per city"),
                List.of("overall", "country", "city"),
                1);
        String dateFrom = io.prompt("dateFrom (optional, YYYY-MM-DD): ");
        String dateTo = io.prompt("dateTo (optional, YYYY-MM-DD): ");
        runAndPrint(3, "R3 Organizer revenue rank", List.of(scope, dateFrom, dateTo));
    }

    private void r4() {
        String windowDays = io.prompt("window in days (optional) [365]: ");
        runAndPrint(4, "R4 Scalper candidates", List.of(windowDays));
    }

    private void r5() {
        String mode = io.choose(
                "R5 mode:",
                List.of("Overall", "Per city"),
                List.of("overall", "per-city"),
                1);
        String dateFrom = io.promptNonEmpty("dateFrom (YYYY-MM-DD): ");
        String dateTo = io.promptNonEmpty("dateTo (YYYY-MM-DD): ");
        runAndPrint(5, "R5 Customer order ranks", List.of(mode, dateFrom, dateTo));
    }

    private void r6() {
        String mode = io.choose(
                "R6 mode:",
                List.of("Customers", "Organizers", "Both"),
                List.of("customers", "organizers", "both"),
                3);
        String windowDays = io.prompt("window in days (optional) [365]: ");
        String limit = io.prompt("limit (optional) [10]: ");
        runAndPrint(6, "R6 Top cancellations", List.of(mode, windowDays, limit));
    }

    private void r7() {
        String mode = io.choose(
                "R7 mode:",
                List.of("Per performance", "Per price tier", "Month and city (sold-out / low)"),
                List.of("perf", "tier", "month"),
                1);
        if (mode.equalsIgnoreCase("month")) {
            String yearMonth = io.promptNonEmpty("month (YYYY-MM): ");
            String city = io.prompt("city filter (optional): ");
            runAndPrint(7, "R7 Sell-through (month x city)", List.of(mode, yearMonth, city));
        } else {
            String performanceID = io.prompt("performanceID filter (optional, blank=all): ");
            runAndPrint(7, "R7 Sell-through (" + mode + ")", List.of(mode, performanceID));
        }
    }

    private void r8() {
        String mode = io.choose(
                "R8 mode:",
                List.of("Per-event stats", "Top 10 by volume in a period"),
                List.of("stats", "top10"),
                1);
        if (mode.equalsIgnoreCase("top10")) {
            String periodFrom = io.promptNonEmpty("periodFrom (YYYY-MM-DD): ");
            String periodTo = io.promptNonEmpty("periodTo (YYYY-MM-DD): ");
            runAndPrint(8, "R8 Resale report (top-10 volume)", List.of(mode, periodFrom, periodTo));
        } else {
            runAndPrint(8, "R8 Resale report (per-event stats)", List.of(mode));
        }
    }

    private void r9() {
        String eventID = io.prompt("eventID filter (optional, blank=all events): ");
        runAndPrint(9, "R9 Noun phrases per event (OpenNLP, Top-10)", List.of(eventID));
    }

    private void runAndPrint(int reportNumber, String title, List<String> params) {
        try {
            List<String> headers = ctx.reports().headersFor(reportNumber, params);
            List<List<String>> rows = ctx.reports().runReport(reportNumber, params);
            Tabulator.printTable(title, headers, rows);
        } catch (RuntimeException e) {
            System.out.println("Report failed: " + e.getMessage());
        }
        io.pause();
    }

    private static MenuAction r(String label, Runnable body) {
        return new MenuAction() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public void run(ConsoleIO io) {
                body.run();
            }
        };
    }
}
