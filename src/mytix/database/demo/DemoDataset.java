package mytix.database.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mytix.nlp.ReviewNounPhraseProjection;
import mytix.util.PasswordUtil;

/** Fresh, date-relative synthetic transaction history; no legacy seed data is read. */
final class DemoDataset {
    static final String PASSWORD = "MyTixDemo!42";
    static final List<String> INSERT_ORDER = List.of("payment_cards", "postal_areas", "segments", "genres",
            "users", "venues", "sections", "seats", "artists", "events", "event_artists", "performances",
            "price_tiers", "performance_section_tiers", "blocked_seats", "orders", "tickets",
            "ticket_ownership", "resale_listings", "reviews", "review_noun_phrases", "postal_adjacencies");
    static final List<String> COMMENTS = List.of(
            "The clear sound and friendly staff made the evening special. The comfortable seats offered a wonderful view.",
            "The friendly staff welcomed our group. The clear sound and lively atmosphere made this a memorable performance.",
            "The comfortable seats and clear sound were excellent. The bright stage lighting complemented a wonderful performance.");

    final LocalDate date;
    final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
    final Map<String, Integer> scenarios = new LinkedHashMap<>();
    private final Map<String, Integer> sequences = new LinkedHashMap<>();
    private final LocalDateTime clock;
    private final List<List<String>> phrases;
    private final List<Person> customers = new ArrayList<>();
    private final List<Person> organizers = new ArrayList<>();
    private final List<Venue> venues = new ArrayList<>();
    private final List<Performance> performances = new ArrayList<>();
    private final List<Purchase> purchases = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();
    private int ordinaryOrder;

    private record Person(int id, String card) {}
    private record Seat(int id, int section) {}
    private record Venue(int id, int index, int main, int balcony, int ga, int gaCapacity, List<Seat> seats) {}
    private record Performance(int id, int event, int eventIndex, int slot, int organizer, Venue venue,
                               LocalDate date, Map<Integer, BigDecimal> prices, Set<Integer> blocked) {}
    private record Purchase(int id, Person buyer, BigDecimal total, LocalDateTime time, Map<String, Object> row) {}
    private static final class Ticket {
        final int id;
        final Performance performance;
        final BigDecimal face;
        final Map<String, Object> row;
        Person owner;
        Purchase acquisition;
        int ownership;
        Ticket(int id, Performance p, BigDecimal face, Map<String, Object> row, Person owner, Purchase order, int ownership) {
            this.id = id; this.performance = p; this.face = face; this.row = row;
            this.owner = owner; this.acquisition = order; this.ownership = ownership;
        }
        boolean active() { return "ACTIVE".equals(row.get("status")); }
    }

    static DemoDataset prepare(LocalDate date) {
        // All expensive or fallible password/model work finishes before deleting any stored data.
        List<List<String>> phrases = COMMENTS.stream().map(ReviewNounPhraseProjection::extract).toList();
        if (phrases.stream().anyMatch(List::isEmpty)) {
            throw new IllegalStateException("Demo comments produced no noun phrases; check the OpenNLP models.");
        }
        DemoDataset data = new DemoDataset(date, phrases);
        data.accounts();
        data.geography();
        data.catalog();
        data.primarySales();
        data.specialTransactions();
        data.reviews();
        data.validate();
        return data;
    }

    private DemoDataset(LocalDate date, List<List<String>> phrases) {
        this.date = date; this.clock = date.atTime(12, 0); this.phrases = phrases;
        INSERT_ORDER.forEach(table -> tables.put(table, new ArrayList<>()));
    }

    Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        tables.forEach((name, rows) -> counts.put(name, (long) rows.size()));
        return counts;
    }

    private int next(String table) { return sequences.merge(table, 1, Integer::sum); }
    private Map<String, Object> add(String table, Object... cells) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i += 2) row.put((String) cells[i], cells[i + 1]);
        tables.get(table).add(row);
        return row;
    }
    private static BigDecimal money(String value) { return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private void accounts() {
        for (int i = 1; i <= 105; i++) {
            boolean organizer = i <= 5;
            int number = organizer ? i : i - 5;
            String card = organizer ? null : "DEMO-CARD-" + String.format("%03d", number);
            if (card != null) add("payment_cards", "cardNumber", card, "cardExpiry", date.plusYears(3).toString().substring(0, 7));
            int id = next("users");
            String email = organizer ? String.format("organizer%02d@demo.mytix.test", number)
                    : String.format("customer%03d@demo.mytix.test", number);
            add("users", "userID", id, "email", email, "passwordHash", PasswordUtil.hash(PASSWORD),
                    "name", (organizer ? "Demo Organizer " : "Demo Customer ") + number,
                    "address", number + " Fictional Demo Lane", "birthday", date.minusYears(20 + number % 45).minusDays(number),
                    "userType", organizer ? "ORGANIZER" : "CUSTOMER", "creditCardNumber", card,
                    "createdAt", clock.minusDays(364), "deletedAt", null);
            (organizer ? organizers : customers).add(new Person(id, card));
        }
        scenarios.put("ordinaryCustomer", customers.get(2).id);
        scenarios.put("firstOrganizer", organizers.get(0).id);
    }

    private void geography() {
        // Real venue locations; capacities/seating below are deliberately small synthetic demo layouts.
        String[][] locations = {
            {"Scotiabank Arena", "40 Bay Street", "M5J 2X2", "Toronto", "Canada", "43.643500", "-79.379100"},
            {"Meridian Hall", "1 Front Street East", "M5E 1B2", "Toronto", "Canada", "43.646600", "-79.376000"},
            {"Roy Thomson Hall", "60 Simcoe Street", "M5J 2H5", "Toronto", "Canada", "43.646500", "-79.386700"},
            {"Rogers Arena", "800 Griffiths Way", "V6B 0N8", "Vancouver", "Canada", "49.277750", "-123.108940"},
            {"Madison Square Garden", "4 Pennsylvania Plaza", "10001", "New York", "United States", "40.750500", "-73.993400"},
            {"Radio City Music Hall", "1260 6th Avenue", "10020", "New York", "United States", "40.760100", "-73.979900"},
            {"Climate Pledge Arena", "334 1st Avenue North", "98109", "Seattle", "United States", "47.622100", "-122.354000"},
            {"Paramount Theatre", "911 Pine Street", "98101", "Seattle", "United States", "47.613500", "-122.331700"}
        };
        for (int v = 0; v < locations.length; v++) {
            String[] place = locations[v];
            add("postal_areas", "postalCode", place[2], "city", place[3], "country", place[4]);
            int id = next("venues");
            add("venues", "venueID", id, "venueName", place[0], "address", place[1], "postalCode", place[2],
                    "latitude", new BigDecimal(place[5]), "longitude", new BigDecimal(place[6]));
            int main = section(id, "Main Floor", false, null);
            int balcony = section(id, "Balcony", false, null);
            int capacity = 24 + v * 4;
            int ga = section(id, "Standing", true, capacity);
            List<Seat> seats = new ArrayList<>();
            for (String row : List.of("A", "B")) for (int n = 1; n <= 12; n++) seats.add(seat(main, row, n));
            for (int n = 1; n <= 11; n += 2) seats.add(seat(balcony, "C", n));
            for (int n = 1; n <= 6; n++) seats.add(seat(balcony, "D", n));
            venues.add(new Venue(id, v, main, balcony, ga, capacity, seats));
        }
        // Curated demo search graph, explicitly not a postal-boundary authority claim.
        for (String[] pair : List.of(new String[] {"M5E 1B2", "M5J 2X2"}, new String[] {"M5J 2H5", "M5J 2X2"},
                new String[] {"M5E 1B2", "M5J 2H5"})) {
            add("postal_adjacencies", "postalCode", pair[0], "adjacentPostalCode", pair[1]);
        }
        scenarios.put("nearbyTorontoVenue", venues.get(0).id);
    }

    private int section(int venue, String name, boolean ga, Integer capacity) {
        int id = next("sections");
        add("sections", "sectionID", id, "venueID", venue, "sectionName", name,
                "isGeneralAdmission", ga ? 1 : 0, "standingCapacity", capacity);
        return id;
    }
    private Seat seat(int section, String row, int number) {
        int id = next("seats");
        add("seats", "seatID", id, "sectionID", section, "rowName", row, "seatNumber", number);
        return new Seat(id, section);
    }

    private void catalog() {
        for (String name : List.of("Music", "Sports", "Arts & Theatre")) {
            add("segments", "segmentID", next("segments"), "segmentName", name);
        }
        String[] genreNames = {"Rock", "Hockey", "Basketball", "Tennis", "Comedy", "Magic & Illusion"};
        int[] genreSegments = {1, 2, 2, 2, 3, 3};
        for (int i = 0; i < genreNames.length; i++) add("genres", "genreID", next("genres"),
                "segmentID", genreSegments[i], "genreName", genreNames[i]);
        String[] names = {"Northern Echoes", "Harbour Lights", "Copper Strings", "Velvet Skyline", "Maple Meteors",
                "Pacific Pioneers", "Metro Comets", "Soundside Sparks", "River City Rackets", "Lakeside Aces",
                "Maya Birch", "Leo North", "The Lantern Circle", "Juniper Illusions", "Aurora Quartet",
                "Cedar Collective", "Riley Stone", "Wren Winters"};
        for (String name : names) add("artists", "artistID", next("artists"), "artistName", name,
                "biography", "Fictional performer or team created exclusively for the MyTix demonstration.");
        String[] titles = {"Northern Echoes Live", "Maple Ice Invitational", "Metro Hoops Showcase", "Lakeside Tennis Open",
                "An Evening of Good Laughs", "Lanterns and Illusions"};
        for (int e = 0; e < 24; e++) {
            int event = next("events");
            int organizer = organizers.get(e % organizers.size()).id;
            add("events", "eventID", event, "organizerID", organizer, "genreID", e % 6 + 1,
                    "title", titles[e % 6] + " — Chapter " + (e / 6 + 1), "resaleCapRatio", money("1.20"));
            add("event_artists", "eventID", event, "artistID", e % 18 + 1, "billingOrder", "HEADLINER");
            add("event_artists", "eventID", event, "artistID", (e + 7) % 18 + 1, "billingOrder", "OPENING_ACT");
            if (e % 3 == 0) add("event_artists", "eventID", event, "artistID", (e + 11) % 18 + 1, "billingOrder", "SPECIAL_GUEST");
            for (int slot = 0; slot < 3; slot++) {
                Venue venue = venues.get(slot == 1 ? (e + 3) % 8 : e % 8);
                int offset = slot == 0 ? -30 - e * 7 : slot == 1 ? (e == 0 ? 3 : 14 + e * 3)
                        : (e % 2 == 0 ? -210 - e * 3 : 90 + e);
                int id = next("performances");
                LocalDate day = date.plusDays(offset);
                add("performances", "performanceID", id, "eventID", event, "venueID", venue.id, "date", day,
                        "startTime", java.time.LocalTime.of(19, 0), "endTime", java.time.LocalTime.of(21, 0),
                        "status", "SCHEDULED", "cancelledAt", null);
                Map<Integer, BigDecimal> prices = new LinkedHashMap<>();
                int premium = tier(id, "Premium", 90 + e % 4 * 10 + slot * 5);
                int standard = tier(id, "Standard", 60 + e % 3 * 5 + slot * 4);
                int value = tier(id, "Value", 30 + e % 4 * 4 + slot * 3);
                mapping(id, venue.main, slot == 2 ? standard : premium, prices);
                mapping(id, venue.balcony, slot == 2 ? premium : standard, prices);
                mapping(id, venue.ga, value, prices);
                Set<Integer> blocked = new LinkedHashSet<>();
                if (e % 3 == 0) {
                    int seat = venue.seats.get(venue.seats.size() - 1).id;
                    blocked.add(seat);
                    add("blocked_seats", "performanceID", id, "seatID", seat, "reason", "Demo production sightline");
                }
                performances.add(new Performance(id, event, e, slot, organizer, venue, day, prices, blocked));
            }
        }
        scenarios.put("soonCancellationClosed", performance(0, 1).id);
        scenarios.put("futureConsecutiveAndGA", performance(1, 1).id);
        scenarios.put("futureUnsoldTier", performance(2, 1).id);
        scenarios.put("sameVenueDifferentTiersA", performance(0, 0).id);
        scenarios.put("sameVenueDifferentTiersB", performance(0, 2).id);
    }

    private int tier(int performance, String name, int price) {
        int id = next("price_tiers");
        add("price_tiers", "tierID", id, "performanceID", performance, "tierName", name, "price", money(Integer.toString(price)));
        return id;
    }
    private void mapping(int performance, int section, int tier, Map<Integer, BigDecimal> prices) {
        add("performance_section_tiers", "performanceID", performance, "sectionID", section, "tierID", tier);
        prices.put(section, (BigDecimal) tables.get("price_tiers").get(tier - 1).get("price"));
    }
    private Performance performance(int eventIndex, int slot) { return performances.get(eventIndex * 3 + slot); }

    private void primarySales() {
        for (Performance p : performances) {
            int sellable = p.venue.seats.size() - p.blocked.size() + p.venue.gaCapacity;
            boolean past = p.date.isBefore(date);
            int amount;
            if (past) {
                boolean full = p.slot == 0 ? p.eventIndex % 4 == 0 || p.eventIndex == 3 : p.eventIndex % 4 == 2;
                boolean low = p.slot == 0 ? p.eventIndex % 4 == 1 || p.eventIndex == 11 : p.eventIndex % 4 == 0;
                amount = full ? sellable : low ? 4 : 27;
                if (full) scenarios.putIfAbsent("pastSoldOut", p.id);
                if (low) scenarios.putIfAbsent("pastLowSales", p.id);
            } else amount = p.eventIndex == 2 && p.slot == 1 ? 0 : 18;
            List<Seat> allocations = p.venue.seats.stream().filter(seat -> !p.blocked.contains(seat.id)).toList();
            int taken = 0;
            while (taken < amount) {
                Person buyer = customers.get(2 + ordinaryOrder++ % 98); // reserve customers 1/2 for exact scalper cohorts
                List<Seat> bundle = new ArrayList<>();
                for (int j = 0; j < 3 && taken < amount; j++, taken++) {
                    bundle.add(taken < allocations.size() ? allocations.get(taken) : new Seat(0, p.venue.ga));
                }
                LocalDateTime bought = past ? p.date.atTime(12, 0).minusDays(14 + p.id % 30)
                        : clock.minusDays(30 + p.id % 25);
                primaryOrder(p, buyer, bundle, bought);
            }
        }
    }

    private List<Ticket> primaryOrder(Performance p, Person buyer, List<Seat> allocations, LocalDateTime bought) {
        BigDecimal total = allocations.stream().map(seat -> p.prices.get(seat.section)).reduce(money("0"), BigDecimal::add);
        Purchase order = order(p, buyer, total, "PRIMARY", bought);
        List<Ticket> created = new ArrayList<>();
        for (Seat seat : allocations) {
            int id = next("tickets");
            BigDecimal price = p.prices.get(seat.section);
            Map<String, Object> row = add("tickets", "ticketID", id, "orderID", order.id, "performanceID", p.id,
                    "sectionID", seat.section, "seatID", seat.id == 0 ? null : seat.id, "currentOwnerID", buyer.id,
                    "faceValue", price, "status", "ACTIVE", "cancelledAt", null, "cancelledBy", null,
                    "cancellationType", null, "refundOrderID", null, "refundedAmount", money("0"));
            int acquisition = ownership(id, order, "PURCHASE");
            Ticket ticket = new Ticket(id, p, price, row, buyer, order, acquisition);
            tickets.add(ticket); created.add(ticket);
        }
        return created;
    }
    private Purchase order(Performance p, Person buyer, BigDecimal total, String type, LocalDateTime time) {
        int id = next("orders");
        Map<String, Object> row = add("orders", "orderID", id, "customerID", buyer.id, "performanceID", p.id,
                "orderType", type, "orderTimestamp", time, "totalAmount", total, "cardNumber", buyer.card,
                "cardExpiry", date.plusYears(2).toString().substring(0, 7), "refundedAmount", money("0"), "status", "COMPLETED");
        Purchase order = new Purchase(id, buyer, total, time, row);
        purchases.add(order);
        return order;
    }
    private int ownership(int ticket, Purchase order, String source) {
        int id = next("ticket_ownership");
        // Primary per-ticket amount comes from face value; resale orders acquire exactly one ticket.
        BigDecimal paid = "RESALE".equals(source) ? order.total
                : (BigDecimal) tables.get("tickets").get(ticket - 1).get("faceValue");
        add("ticket_ownership", "ownershipID", id, "ticketID", ticket, "ownerID", order.buyer.id,
                "orderID", order.id, "amountPaid", paid, "acquiredAt", order.time, "source", source);
        return id;
    }

    private List<Ticket> forPerformance(Performance p) { return tickets.stream().filter(t -> t.performance == p).toList(); }
    private void specialTransactions() {
        Performance cohort = performance(5, 1); // Scotiabank Arena, Toronto, future with sufficient GA stock.
        for (int c = 0; c < 2; c++) {
            Person person = customers.get(c);
            List<Seat> ga = java.util.Collections.nCopies(10, new Seat(0, cohort.venue.ga));
            List<Ticket> held = primaryOrder(cohort, person, ga, clock.minusDays(25));
            listing(held.get(0), "1.10", "WITHDRAWN", clock.minusDays(20), null, null);
            for (int i = 0; i < 6; i++) listing(held.get(i), i % 2 == 0 ? "1.20" : "1.10", "ACTIVE",
                    clock.minusDays(18).plusHours(i), null, null);
            scenarios.put("flaggedScalper" + (c + 1), person.id);
        }
        Ticket twice = forPerformance(performance(1, 1)).get(0);
        resell(twice, customers.get(96), "1.10", clock.minusDays(12));
        resell(twice, customers.get(97), "1.20", clock.minusDays(8));
        listing(twice, "1.20", "ACTIVE", clock.minusDays(5), null, null);
        scenarios.put("twiceResoldTicket", twice.id);
        Ticket relisted = forPerformance(performance(3, 1)).get(0);
        listing(relisted, "1.15", "WITHDRAWN", clock.minusDays(14), null, null);
        listing(relisted, "1.20", "ACTIVE", clock.minusDays(7), null, null);

        Ticket resaleRefund = forPerformance(performance(4, 1)).get(0);
        resell(resaleRefund, customers.get(98), "1.15", clock.minusDays(10));
        cancel(resaleRefund, "CUSTOMER", resaleRefund.owner.id, clock.minusDays(4));
        scenarios.put("refundedResaleTicket", resaleRefund.id);
        for (int e : List.of(2, 10, 14)) {
            Performance p = performance(e, 0);
            List<Ticket> sold = forPerformance(p);
            for (int i = 0; i < 2; i++) cancel(sold.get(i), "CUSTOMER", sold.get(i).owner.id, p.date.atTime(12, 0).minusDays(10));
        }
        for (int e : List.of(8, 9)) {
            Ticket ticket = forPerformance(performance(e, 1)).get(0);
            cancel(ticket, "CUSTOMER", ticket.owner.id, clock.minusDays(3));
        }
        for (int e : List.of(6, 7)) {
            Performance p = performance(e, 1);
            LocalDateTime at = clock.minusDays(e - 4);
            Map<String, Object> row = tables.get("performances").get(p.id - 1);
            row.put("status", "CANCELLED"); row.put("cancelledAt", at);
            for (Ticket ticket : forPerformance(p)) if (ticket.active()) cancel(ticket, "ORGANIZER", p.organizer, at);
            scenarios.put("cancelledPerformance" + (e - 5), p.id);
        }
    }
    private void listing(Ticket ticket, String multiplier, String status, LocalDateTime created, Person buyer, LocalDateTime sold) {
        add("resale_listings", "listingID", next("resale_listings"), "ticketID", ticket.id,
                "sellerID", ticket.owner.id, "ownershipID", ticket.ownership, "buyerID", buyer == null ? null : buyer.id,
                "listingPrice", money(ticket.face.multiply(new BigDecimal(multiplier))), "capRatio", money("1.20"),
                "status", status, "createdAt", created, "soldAt", sold);
    }
    private void resell(Ticket ticket, Person buyer, String multiplier, LocalDateTime sold) {
        if (ticket.owner.id == buyer.id) throw new IllegalStateException("Demo resale buyer must differ from seller.");
        listing(ticket, multiplier, "SOLD", sold.minusDays(1), buyer, sold);
        BigDecimal amount = money(ticket.face.multiply(new BigDecimal(multiplier)));
        Purchase order = order(ticket.performance, buyer, amount, "RESALE", sold);
        ticket.ownership = ownership(ticket.id, order, "RESALE");
        ticket.acquisition = order; ticket.owner = buyer; ticket.row.put("currentOwnerID", buyer.id);
    }
    private void cancel(Ticket ticket, String type, int actor, LocalDateTime at) {
        BigDecimal paid = (BigDecimal) tables.get("ticket_ownership").get(ticket.ownership - 1).get("amountPaid");
        ticket.row.put("status", "CUSTOMER".equals(type) ? "CANCELLED" : "REFUNDED");
        ticket.row.put("cancelledAt", at); ticket.row.put("cancelledBy", actor); ticket.row.put("cancellationType", type);
        ticket.row.put("refundOrderID", ticket.acquisition.id); ticket.row.put("refundedAmount", paid);
        BigDecimal totalRefund = ((BigDecimal) ticket.acquisition.row.get("refundedAmount")).add(paid);
        ticket.acquisition.row.put("refundedAmount", totalRefund);
        if (totalRefund.compareTo(ticket.acquisition.total) == 0) ticket.acquisition.row.put("status", "CANCELLED");
        for (Map<String, Object> listing : tables.get("resale_listings")) {
            if (listing.get("ticketID").equals(ticket.id) && "ACTIVE".equals(listing.get("status"))) listing.put("status", "WITHDRAWN");
        }
    }

    private void reviews() {
        for (int e = 0; e < 24; e++) {
            Performance p = performance(e, 0);
            Set<Integer> reviewed = new LinkedHashSet<>();
            for (Ticket ticket : forPerformance(p)) {
                if (!ticket.active() || !reviewed.add(ticket.owner.id)) continue;
                int id = next("reviews");
                int template = (reviewed.size() - 1) % COMMENTS.size();
                add("reviews", "reviewID", id, "customerID", ticket.owner.id, "performanceID", p.id,
                        "eventRating", 3 + (e + template) % 3, "venueRating", 4 + template % 2,
                        "commentText", COMMENTS.get(template), "createdAt", p.date.atTime(12, 0).plusDays(1 + template));
                int index = 1;
                for (String phrase : phrases.get(template)) add("review_noun_phrases", "reviewID", id,
                        "phraseIndex", index++, "nounPhrase", phrase);
                if (reviewed.size() == 3) break;
            }
        }
    }

    private void validate() {
        if (tables.get("orders").size() < 300 || tickets.size() < 800 || tables.get("reviews").size() < 24) {
            throw new IllegalStateException("Generated demo does not satisfy the requested dataset minimums.");
        }
        for (Performance p : performances) {
            long ga = forPerformance(p).stream().filter(Ticket::active)
                    .filter(t -> t.row.get("seatID") == null).count();
            if (ga > p.venue.gaCapacity) throw new IllegalStateException("Generated demo oversells standing capacity.");
        }
        for (Purchase order : purchases) {
            BigDecimal acquired = tables.get("ticket_ownership").stream().filter(row -> row.get("orderID").equals(order.id))
                    .map(row -> (BigDecimal) row.get("amountPaid")).reduce(money("0"), BigDecimal::add);
            if (acquired.compareTo(order.total) != 0) throw new IllegalStateException("Generated order does not reconcile with acquisitions.");
        }
    }
}
