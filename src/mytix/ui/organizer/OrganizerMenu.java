package mytix.ui.organizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mytix.AppContext;
import mytix.database.DatabaseLogistics;
import mytix.database.organizer.CreateEventRequest;
import mytix.database.organizer.CreatePerformanceRequest;
import mytix.database.user.UserRecord;
import mytix.session.SessionContext;
import mytix.ui.AbstractMenu;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;
import mytix.util.Tabulator;

public final class OrganizerMenu extends AbstractMenu {

    private final AppContext ctx;

    public OrganizerMenu(ConsoleIO io, AppContext ctx) {
        super(io, "Organizer Menu", "7", "Logout / Back");
        this.ctx = ctx;
    }

    @Override
    public void loop() {
        try {
            super.loop();
        } finally {
            ctx.session().logout();
        }
    }

    @Override
    protected Map<String, MenuAction> actions() {
        Map<String, MenuAction> m = new LinkedHashMap<>();
        m.put("0", act("Login", this::login));
        m.put("1", act("Create Event", () -> gated(this::createEvent)));
        m.put("2", act("Create Performance (+ Toolkit)", () -> gated(this::createPerformance)));
        m.put("3", act("Update tier price", () -> gated(this::updateTier)));
        m.put("4", act("Block seat", () -> gated(this::blockSeatFlow)));
        m.put("5", act("Unblock seat", () -> gated(this::unblockSeatFlow)));
        m.put("6", act("Cancel performance", () -> gated(this::cancelPerf)));
        return m;
    }

    private void login() {
        SessionContext session = ctx.session();
        if (session.isLoggedIn()) {
            session.logout();
        }
        String email = io.promptNonEmpty("Email: ");
        String password = io.promptNonEmpty("Password: ");
        if (!DatabaseLogistics.isConfigured()) {
            System.out.println("Database offline — cannot authenticate. "
                    + "Seeded organizers use password: password");
            io.pause();
            return;
        }
        try {
            var user = ctx.users().authenticate(email, password, "ORGANIZER");
            if (user.isEmpty()) {
                System.out.println("Login failed (bad credentials or not an ORGANIZER).");
            } else {
                UserRecord u = user.get();
                session.login(u.userID(), u.email(), u.userType(), u.name());
                System.out.println("Logged in as " + session);
            }
        } catch (RuntimeException e) {
            System.out.println("Login error: " + e.getMessage());
        }
        io.pause();
    }

    private void gated(Runnable r) {
        if (!ctx.session().isLoggedIn() || !ctx.session().isOrganizer()) {
            System.out.println("Please login first as ORGANIZER (option 0).");
            io.pause();
            return;
        }
        if (!DatabaseLogistics.isConfigured()) {
            System.out.println("Database offline — this action requires a live connection.");
            io.pause();
            return;
        }
        try {
            r.run();
        } catch (RuntimeException e) {
            System.out.println("Error: " + e.getMessage());
            io.pause();
        }
    }

    private void createEvent() {
        int organizerID = ctx.session().getUserID();

        if (!Tabulator.printTableRequireRows(
                "Segments",
                List.of("segmentID", "segmentName"),
                ctx.catalog().listSegments(),
                "No segments available.")) {
            io.pause();
            return;
        }
        Integer segmentID = promptInt("segmentID: ");
        if (segmentID == null) {
            return;
        }

        List<List<String>> allGenres = ctx.catalog().listGenres();
        List<List<String>> segmentGenres = new ArrayList<>();
        for (List<String> row : allGenres) {
            if (row.get(2).equals(String.valueOf(segmentID))) {
                segmentGenres.add(row);
            }
        }
        if (!Tabulator.printTableRequireRows(
                "Genres in segment " + segmentID,
                List.of("genreID", "genreName", "segmentID"),
                segmentGenres,
                "No genres in this segment.")) {
            io.pause();
            return;
        }
        Integer genreID = promptInt("genreID: ");
        if (genreID == null) {
            return;
        }

        String title = io.promptNonEmpty("Title: ");
        BigDecimal resaleCapRatio = promptDecimalOrDefault("resaleCapRatio", new BigDecimal("2.00"));

        if (!Tabulator.printTableRequireRows(
                "Artists",
                List.of("artistID", "artistName"),
                ctx.catalog().listArtists(),
                "No artists available.")) {
            io.pause();
            return;
        }
        String artistLine = io.promptNonEmpty(
                "artistIDs in billing order, comma-separated "
                        + "(first=HEADLINER, last=OPENING_ACT, middle=SPECIAL_GUEST): ");
        List<Integer> artistIDs = new ArrayList<>();
        for (String tok : artistLine.split(",")) {
            tok = tok.trim();
            if (tok.isEmpty()) {
                continue;
            }
            try {
                artistIDs.add(Integer.parseInt(tok));
            } catch (NumberFormatException e) {
                System.out.println("Skipping invalid artistID: " + tok);
            }
        }
        if (artistIDs.isEmpty()) {
            System.out.println("At least one artist is required. Aborting.");
            io.pause();
            return;
        }

        int eventID = ctx.organizer().createEvent(
                new CreateEventRequest(organizerID, genreID, title, resaleCapRatio, artistIDs));
        System.out.println("Created event #" + eventID + " (\"" + title + "\").");
        io.pause();
    }

    private void createPerformance() {
        int organizerID = ctx.session().getUserID();

        if (!Tabulator.printTableRequireRows(
                "My Events",
                List.of("eventID", "title", "genre", "organizerID"),
                ctx.catalog().listEvents(organizerID),
                "No events available.")) {
            io.pause();
            return;
        }
        Integer eventID = promptInt("eventID: ");
        if (eventID == null) {
            return;
        }
        Integer genreID = ctx.catalog().eventGenreID(eventID);
        if (genreID == null) {
            System.out.println("Event not found.");
            io.pause();
            return;
        }

        if (!Tabulator.printTableRequireRows(
                "Venues",
                List.of("venueID", "venueName", "city", "postalCode"),
                ctx.catalog().listVenues(),
                "No venues available.")) {
            io.pause();
            return;
        }
        Integer venueID = promptInt("venueID: ");
        if (venueID == null) {
            return;
        }

        LocalDate date = promptDate("date (YYYY-MM-DD): ");
        if (date == null) {
            return;
        }
        LocalTime startTime = promptTime("startTime (HH:MM): ");
        if (startTime == null) {
            return;
        }
        LocalTime endTime = promptTime("endTime (HH:MM): ");
        if (endTime == null) {
            return;
        }
        if (!endTime.isAfter(startTime)) {
            System.out.println("endTime must be after startTime. Aborting.");
            io.pause();
            return;
        }

        List<List<String>> suggestion = ctx.organizer().suggestPricing(venueID, genreID, date);
        Tabulator.printTable(
                "Toolkit Suggestion (comparable-sample pricing)",
                List.of("tier", "capacityShare", "suggestedPrice", "sellThrough", "confidence", "Estimated revenue change"),
                suggestion);

        Map<String, BigDecimal> tierPrices = new LinkedHashMap<>();
        boolean accept = io.confirmYesNo("Accept toolkit? Prefills tiers P1..Pk", true);
        if (accept) {
            for (List<String> row : suggestion) {
                try {
                    tierPrices.put(row.get(0), new BigDecimal(row.get(2)));
                } catch (NumberFormatException e) {
                    System.out.println("Skipping unparsable suggested price for " + row.get(0));
                }
            }
            System.out.println("Prefilled " + tierPrices.size() + " tier(s) from the toolkit.");
        } else {
            System.out.println("Manual tier entry (need >= 2). Enter blank tierName to stop.");
            while (true) {
                String tierName = io.prompt("tierName (blank to stop): ");
                if (tierName.isEmpty()) {
                    if (tierPrices.size() >= 2) {
                        break;
                    }
                    System.out.println("At least 2 tiers are required before stopping.");
                    continue;
                }
                BigDecimal price = promptDecimalOrDefault("price for " + tierName, null);
                if (price == null) {
                    System.out.println("Invalid price, tier not added.");
                    continue;
                }
                tierPrices.put(tierName, price);
            }
        }

        if (!suggestion.isEmpty() && io.confirmYesNo("Estimate revenue change for a toolkit tier?", true)) {
            estimateDeltaRevFlow(venueID, suggestion);
        }

        if (tierPrices.size() < 2) {
            System.out.println("At least 2 price tiers are required. Aborting.");
            io.pause();
            return;
        }

        List<List<String>> sections = ctx.catalog().listSectionsForVenue(venueID);
        if (sections.isEmpty()) {
            System.out.println("Venue has no sections defined. Aborting.");
            io.pause();
            return;
        }
        Tabulator.printTable(
                "Venue Sections (assign EVERY row to one of your tiers: "
                        + String.join(", ", tierPrices.keySet()) + ")",
                List.of("sectionID", "sectionName", "kind", "standingCapacity"),
                sections);

        List<String> tierNameLabels = new ArrayList<>();
        List<String> tierNameValues = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : tierPrices.entrySet()) {
            tierNameLabels.add(entry.getKey() + " ($" + entry.getValue().toPlainString() + ")");
            tierNameValues.add(entry.getKey());
        }

        Map<Integer, String> sectionToTier = new LinkedHashMap<>();
        for (List<String> row : sections) {
            int sectionID = Integer.parseInt(row.get(0));
            String label = row.get(1) + " (" + row.get(2) + ")";
            String tierName = io.choose(
                    "Tier for section " + sectionID + " [" + label + "]", tierNameLabels, tierNameValues, 0);
            sectionToTier.put(sectionID, tierName);
        }

        int performanceID = ctx.organizer().createPerformance(new CreatePerformanceRequest(
                organizerID, eventID, venueID, date, startTime, endTime, tierPrices, sectionToTier));
        System.out.println("Created performance #" + performanceID + ".");
        io.pause();
    }

    private void estimateDeltaRevFlow(int venueID, List<List<String>> suggestion) {
        Integer targetCapacity = ctx.organizer().targetCapacity(venueID);
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (List<String> r : suggestion) {
            labels.add(r.get(0));
            values.add(r.get(0));
        }
        String tierLabel = io.choose(
                "Which toolkit suggestion tier (P1/P2/P3 — not your manually entered tier prices)?",
                labels,
                values,
                0);
        List<String> row = null;
        for (List<String> r : suggestion) {
            if (r.get(0).equalsIgnoreCase(tierLabel)) {
                row = r;
                break;
            }
        }
        if (row == null) {
            System.out.println("Unknown tier label.");
            return;
        }
        double price;
        double share;
        double sellThrough;
        try {
            price = Double.parseDouble(row.get(2));
            share = parsePercent(row.get(1));
            sellThrough = row.get(3).equals("n/a") ? 0.0 : parsePercent(row.get(3));
        } catch (NumberFormatException e) {
            System.out.println("Cannot parse toolkit row for estimated revenue change.");
            return;
        }
        double ct = targetCapacity * share;
        BigDecimal deltaInput = promptDecimalOrDefault(
                "delta = (newPrice - price) / price, e.g. -0.10 for -10%", BigDecimal.ZERO);
        double estimatedRevenueChange = ctx.organizer()
                .estimateDeltaRevenue(price, ct, sellThrough, deltaInput.doubleValue());
        System.out.printf(
                "Estimated revenue change for %s: %.2f (targetCapacity=%d, Ct=%.1f, s=%.3f, E=-1.0; "
                        + "not causal, illustrative only)%n",
                tierLabel, estimatedRevenueChange, targetCapacity, ct, sellThrough);
        io.pause();
    }

    private static double parsePercent(String pctString) {
        return Double.parseDouble(pctString.replace("%", "")) / 100.0;
    }

    private void updateTier() {
        int organizerID = ctx.session().getUserID();
        if (!Tabulator.printTableRequireRows(
                "My Performances",
                List.of("performanceID", "event", "venue", "date", "startTime", "status"),
                ctx.catalog().listMyPerformances(organizerID),
                "No performances available.")) {
            io.pause();
            return;
        }
        Integer performanceID = promptInt("performanceID: ");
        if (performanceID == null) {
            return;
        }

        if (!Tabulator.printTableRequireRows(
                "Tiers",
                List.of("tierID", "tierName", "price", "activeTickets"),
                ctx.catalog().listTiers(performanceID),
                "No tiers for this performance.")) {
            io.pause();
            return;
        }
        Integer tierID = promptInt("tierID: ");
        if (tierID == null) {
            return;
        }
        BigDecimal newPrice = promptDecimalOrDefault("newPrice", null);
        if (newPrice == null) {
            System.out.println("Invalid price. Aborting.");
            io.pause();
            return;
        }

        boolean ok = ctx.organizer().updateTierPrice(organizerID, tierID, newPrice);
        if (ok) {
            System.out.println("Tier price updated.");
        } else {
            System.out.println("Refused: this tier still has ACTIVE tickets sold.");
        }
        io.pause();
    }

    private void blockSeatFlow() {
        int organizerID = ctx.session().getUserID();
        if (!Tabulator.printTableRequireRows(
                "My Performances",
                List.of("performanceID", "event", "venue", "date", "startTime", "status"),
                ctx.catalog().listMyPerformances(organizerID),
                "No performances available.")) {
            io.pause();
            return;
        }
        Integer performanceID = promptInt("performanceID: ");
        if (performanceID == null) {
            return;
        }

        if (!Tabulator.printTableRequireRows(
                "Blockable Seats (reserved, unsold, unblocked)",
                List.of("seatID", "section", "seat", "tierName", "price"),
                ctx.catalog().listAvailableReservedSeats(performanceID),
                "No blockable seats for this performance.")) {
            io.pause();
            return;
        }
        Integer seatID = promptInt("seatID: ");
        if (seatID == null) {
            return;
        }
        String reasonInput = io.prompt("reason (optional): ");
        String reason = reasonInput.isEmpty() ? null : reasonInput;

        boolean ok = ctx.organizer().blockSeat(organizerID, performanceID, seatID, reason);
        if (ok) {
            System.out.println("Seat blocked.");
        } else {
            System.out.println("Refused: seat currently has an ACTIVE ticket.");
        }
        io.pause();
    }

    private void unblockSeatFlow() {
        int organizerID = ctx.session().getUserID();
        if (!Tabulator.printTableRequireRows(
                "My Performances",
                List.of("performanceID", "event", "venue", "date", "startTime", "status"),
                ctx.catalog().listMyPerformances(organizerID),
                "No performances available.")) {
            io.pause();
            return;
        }
        Integer performanceID = promptInt("performanceID: ");
        if (performanceID == null) {
            return;
        }

        if (!Tabulator.printTableRequireRows(
                "Blocked Seats",
                List.of("seatID", "section", "seat", "reason"),
                ctx.catalog().listBlockedSeats(performanceID),
                "No blocked seats for this performance.")) {
            io.pause();
            return;
        }
        Integer seatID = promptInt("seatID: ");
        if (seatID == null) {
            return;
        }

        boolean ok = ctx.organizer().unblockSeat(organizerID, performanceID, seatID);
        System.out.println(ok ? "Seat unblocked." : "That seat was not blocked for this performance.");
        io.pause();
    }

    private void cancelPerf() {
        int organizerID = ctx.session().getUserID();
        if (!Tabulator.printTableRequireRows(
                "My Performances",
                List.of("performanceID", "event", "venue", "date", "startTime", "status"),
                ctx.catalog().listMyPerformances(organizerID),
                "No performances available.")) {
            io.pause();
            return;
        }
        Integer performanceID = promptInt("performanceID: ");
        if (performanceID == null) {
            return;
        }
        boolean confirm = io.confirmYesNo(
                "Cancel performance #" + performanceID
                        + "? ACTIVE tickets will be REFUNDED and ACTIVE listings withdrawn.",
                true);
        if (!confirm) {
            System.out.println("Cancelled action (no change made).");
            io.pause();
            return;
        }

        boolean ok = ctx.organizer().cancelPerformance(organizerID, performanceID);
        System.out.println(ok ? "Performance cancelled; tickets refunded, listings withdrawn."
                : "Performance was already cancelled.");
        io.pause();
    }

    private Integer promptInt(String label) {
        String raw = io.prompt(label);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid integer. Aborting.");
            io.pause();
            return null;
        }
    }

    private LocalDate promptDate(String label) {
        String raw = io.promptNonEmpty(label);
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date (expected YYYY-MM-DD). Aborting.");
            io.pause();
            return null;
        }
    }

    private LocalTime promptTime(String label) {
        String raw = io.promptNonEmpty(label);
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            System.out.println("Invalid time (expected HH:MM). Aborting.");
            io.pause();
            return null;
        }
    }

    private BigDecimal promptDecimalOrDefault(String label, BigDecimal defaultValue) {
        String suffix = defaultValue == null ? ": " : " [" + defaultValue.toPlainString() + "]: ";
        String raw = io.prompt(label + suffix);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static MenuAction act(String label, Runnable r) {
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
