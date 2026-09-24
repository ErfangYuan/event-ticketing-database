package mytix.ui.customer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mytix.AppContext;
import mytix.database.DatabaseLogistics;
import mytix.database.transaction.BookRequest;
import mytix.database.user.UserRecord;
import mytix.session.SessionContext;
import mytix.ui.AbstractMenu;
import mytix.ui.MenuAction;
import mytix.util.ConsoleIO;
import mytix.util.Tabulator;

public final class CustomerMenu extends AbstractMenu {

    private final AppContext ctx;

    public CustomerMenu(ConsoleIO io, AppContext ctx) {
        super(io, "Customer Menu", "8", "Logout / Back");
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
        m.put("0", named("Login", this::login));
        m.put("1", named("Book tickets", () -> gated("Book tickets", this::book)));
        m.put("2", named("Cancel my ticket", () -> gated("Cancel ticket", this::cancelTicket)));
        m.put("3", named("View my tickets", () -> gated("View tickets", this::viewTickets)));
        m.put("4", named("List ticket for resale", () -> gated("List for resale", this::listForResale)));
        m.put("5", named("Withdraw resale listing", () -> gated("Withdraw listing", this::withdrawListing)));
        m.put("6", named("Buy a resale listing", () -> gated("Buy listing", this::buyResaleListing)));
        m.put("7", named("Write a review", () -> gated("Write review", this::writeReview)));
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
                    + "Sample passwords for seeded users: password");
            io.pause();
            return;
        }
        try {
            var user = ctx.users().authenticate(email, password, "CUSTOMER");
            if (user.isEmpty()) {
                System.out.println("Login failed (bad credentials or not a CUSTOMER).");
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

    private void gated(String name, Runnable body) {
        if (!ctx.session().isLoggedIn() || !ctx.session().isCustomer()) {
            System.out.println("Please login first as CUSTOMER (option 0).");
            io.pause();
            return;
        }
        if (!DatabaseLogistics.isConfigured()) {
            System.out.println("Database offline — cannot perform " + name + ".");
            io.pause();
            return;
        }
        body.run();
    }

    private void book() {
        try {
            List<List<String>> events = ctx.catalog().listUpcomingEvents();
            if (!Tabulator.printTableRequireRows(
                    "Upcoming Events",
                    List.of("eventID", "title", "genre", "segment"),
                    events,
                    "No upcoming events.")) {
                io.pause();
                return;
            }
            Integer eventID = promptInt("eventID");
            if (eventID == null) {
                io.pause();
                return;
            }

            List<List<String>> perfs = ctx.catalog().listUpcomingPerformances(eventID);
            if (!Tabulator.printTableRequireRows(
                    "Upcoming Performances",
                    List.of("performanceID", "date", "start", "end", "venue", "city"),
                    perfs,
                    "No upcoming performances for this event.")) {
                io.pause();
                return;
            }
            Integer performanceID = promptInt("performanceID");
            if (performanceID == null) {
                io.pause();
                return;
            }

            List<List<String>> seatMap = ctx.catalog().listSeatMapPreview(performanceID);
            Tabulator.printTable(
                    "Seat Map Preview",
                    List.of("sectionID", "section", "kind", "tier", "price", "available", "sold", "blocked"),
                    seatMap);

            List<Integer> seatIDs = new ArrayList<>();
            if (io.confirmYesNo("Book reserved seats?", true)) {
                List<List<String>> availableSeats =
                        ctx.catalog().listAvailableReservedSeats(performanceID);
                if (Tabulator.printTableRequireRows(
                        "Available Reserved Seats",
                        List.of("seatID", "section", "seat", "tier", "price"),
                        availableSeats,
                        "No reserved seats available.")) {
                    Set<Integer> allowed = new HashSet<>();
                    for (List<String> row : availableSeats) {
                        allowed.add(Integer.parseInt(row.get(0)));
                    }
                    while (true) {
                        seatIDs.clear();
                        String raw = io.prompt("Enter seatIDs (comma-separated), blank for none: ");
                        if (raw.isBlank()) {
                            break;
                        }
                        boolean ok = true;
                        for (String token : raw.split(",")) {
                            token = token.trim();
                            if (token.isEmpty()) {
                                continue;
                            }
                            try {
                                int seatID = Integer.parseInt(token);
                                if (!allowed.contains(seatID)) {
                                    System.out.println(
                                            "seatID " + seatID
                                                    + " is not in the available reserved seats list.");
                                    ok = false;
                                    break;
                                }
                                if (!seatIDs.contains(seatID)) {
                                    seatIDs.add(seatID);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid seatID: " + token);
                                ok = false;
                                break;
                            }
                        }
                        if (ok) {
                            break;
                        }
                        System.out.println("Please re-enter seatIDs from the table above.");
                    }
                }
            }

            Integer gaSectionID = null;
            int gaQuantity = 0;
            if (io.confirmYesNo("Book General Admission tickets?", true)) {
                List<List<String>> gaSections = new ArrayList<>();
                for (List<String> row : seatMap) {
                    if ("GA".equalsIgnoreCase(row.get(2))) {
                        gaSections.add(row);
                    }
                }
                if (gaSections.isEmpty()) {
                    System.out.println("No GA sections available for this performance.");
                } else if (gaSections.size() == 1) {
                    gaSectionID = Integer.parseInt(gaSections.get(0).get(0));
                    System.out.println(
                            "Using GA section "
                                    + gaSections.get(0).get(1)
                                    + " (sectionID "
                                    + gaSectionID
                                    + ", "
                                    + gaSections.get(0).get(5)
                                    + " available @ "
                                    + gaSections.get(0).get(4)
                                    + ").");
                    Integer qty = promptInt("GA quantity");
                    gaQuantity = qty == null ? 0 : qty;
                } else {
                    Tabulator.printTable(
                            "GA Sections",
                            List.of("sectionID", "section", "kind", "tier", "price", "available", "sold", "blocked"),
                            gaSections);
                    gaSectionID = promptInt("GA sectionID");
                    Integer qty = promptInt("GA quantity");
                    gaQuantity = qty == null ? 0 : qty;
                }
            }

            if (seatIDs.isEmpty() && gaQuantity <= 0) {
                System.out.println("Nothing selected; booking cancelled.");
                io.pause();
                return;
            }

            int customerID = ctx.session().getUserID();
            int orderID = ctx.booking().bookTickets(
                    new BookRequest(customerID, performanceID, seatIDs, gaSectionID, gaQuantity));
            System.out.println("Booked! orderID = " + orderID);
        } catch (RuntimeException e) {
            System.out.println("Booking failed: " + e.getMessage());
        }
        io.pause();
    }

    private void cancelTicket() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> myTickets = ctx.catalog().listCustomerTickets(customerID);
            if (!Tabulator.printTableRequireRows(
                    "My Tickets",
                    List.of("ticketID", "event", "date", "section", "seat", "status", "faceValue"),
                    myTickets,
                    "No tickets to cancel.")) {
                io.pause();
                return;
            }
            Integer ticketID = promptInt("ticketID to cancel");
            if (ticketID == null) {
                io.pause();
                return;
            }
            if (!io.confirmYesNo("Confirm cancel ticket " + ticketID + "?", true)) {
                System.out.println("Cancellation aborted.");
                io.pause();
                return;
            }
            boolean ok = ctx.booking().cancelTicket(customerID, ticketID);
            System.out.println(ok ? "Ticket cancelled." : "Cancel failed.");
        } catch (RuntimeException e) {
            System.out.println("Cancel failed: " + e.getMessage());
        }
        io.pause();
    }

    private void viewTickets() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> tickets = ctx.catalog().listCustomerTickets(customerID);
            if (!Tabulator.printTableRequireRows(
                    "My Tickets / Order History",
                    List.of("ticketID", "event", "date", "section", "seat", "status", "faceValue"),
                    tickets,
                    "No tickets to view.")) {
                io.pause();
                return;
            }
            String pick = io.prompt("View detail for ticketID (blank to skip): ");
            if (!pick.isEmpty()) {
                try {
                    int ticketID = Integer.parseInt(pick);
                    Tabulator.printTable(
                            "Ticket Detail",
                            List.of("ticketID", "ownershipCount", "activeListing"),
                            ctx.catalog().ticketDetail(ticketID));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid ticketID.");
                }
            }
        } catch (RuntimeException e) {
            System.out.println("Error: " + e.getMessage());
        }
        io.pause();
    }

    private void listForResale() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> myTickets = ctx.catalog().listCustomerTickets(customerID);
            if (!Tabulator.printTableRequireRows(
                    "My Tickets",
                    List.of("ticketID", "event", "date", "section", "seat", "status", "faceValue"),
                    myTickets,
                    "No tickets available to list for resale.")) {
                io.pause();
                return;
            }
            Integer ticketID = promptInt("ticketID to list for resale");
            if (ticketID == null) {
                io.pause();
                return;
            }
            BigDecimal askingPrice = promptPrice("Asking price");
            if (askingPrice == null) {
                io.pause();
                return;
            }
            int listingID = ctx.booking().listForResale(customerID, ticketID, askingPrice);
            System.out.println("Listed. listingID = " + listingID);
        } catch (RuntimeException e) {
            System.out.println("List for resale failed: " + e.getMessage());
        }
        io.pause();
    }

    private void withdrawListing() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> myListings = ctx.catalog().listActiveListingsForSeller(customerID);
            if (!Tabulator.printTableRequireRows(
                    "My Active Listings",
                    List.of("listingID", "ticketID", "event", "date", "section", "seat", "listingPrice"),
                    myListings,
                    "No active listings to withdraw.")) {
                io.pause();
                return;
            }
            Integer listingID = promptInt("listingID to withdraw");
            if (listingID == null) {
                io.pause();
                return;
            }
            boolean ok = ctx.booking().withdrawListing(customerID, listingID);
            System.out.println(ok ? "Listing withdrawn." : "Withdraw failed.");
        } catch (RuntimeException e) {
            System.out.println("Withdraw failed: " + e.getMessage());
        }
        io.pause();
    }

    private void buyResaleListing() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> listings = ctx.catalog().listActiveResaleListings(customerID);
            if (!Tabulator.printTableRequireRows(
                    "Active Resale Listings",
                    List.of("listingID", "event", "date", "section", "seat", "faceValue", "listingPrice", "sellerID"),
                    listings,
                    "No active resale listings to buy.")) {
                io.pause();
                return;
            }
            Integer listingID = promptInt("listingID to buy");
            if (listingID == null) {
                io.pause();
                return;
            }
            int orderID = ctx.booking().buyResaleListing(customerID, listingID);
            System.out.println("Purchased! orderID = " + orderID);
        } catch (RuntimeException e) {
            System.out.println("Buy failed: " + e.getMessage());
        }
        io.pause();
    }

    private void writeReview() {
        try {
            int customerID = ctx.session().getUserID();
            List<List<String>> eligiblePerfs = ctx.catalog().listEligibleReviewPerformances(customerID);
            if (!Tabulator.printTableRequireRows(
                    "Eligible Performances",
                    List.of("performanceID", "event", "venue", "date"),
                    eligiblePerfs,
                    "No eligible performances to review.")) {
                io.pause();
                return;
            }
            Integer performanceID = promptInt("performanceID to review");
            if (performanceID == null) {
                io.pause();
                return;
            }
            Integer eventRating = promptInt("Event rating (1-5)");
            Integer venueRating = promptInt("Venue rating (1-5)");
            if (eventRating == null || venueRating == null) {
                io.pause();
                return;
            }
            String comment = io.promptNonEmpty("Comment: ");
            ctx.booking().writeReview(customerID, performanceID, eventRating, venueRating, comment);
            System.out.println("Review submitted.");
        } catch (RuntimeException e) {
            System.out.println("Write review failed: " + e.getMessage());
        }
        io.pause();
    }

    private Integer promptInt(String label) {
        String raw = io.prompt(label + ": ");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            System.out.println("Invalid integer.");
            return null;
        }
    }

    private BigDecimal promptPrice(String label) {
        String raw = io.prompt(label + ": ");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            System.out.println("Invalid price.");
            return null;
        }
    }

    private static MenuAction named(String label, Runnable r) {
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
