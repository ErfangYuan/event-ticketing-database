package mytix.database.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mytix.database.DatabaseLogistics;
import mytix.nlp.NounPhraseExtractor;
import mytix.nlp.OpenNlpNounPhraseExtractor;

public final class JdbcReportService implements ReportService {

    private static final String PERF_CAPACITY_CTE =
            """
            perf_capacity AS (
              SELECT pst.performanceID,
                SUM(CASE WHEN sec.isGeneralAdmission = 1 THEN sec.standingCapacity
                         ELSE (SELECT COUNT(*) FROM seats se WHERE se.sectionID = sec.sectionID) END) AS totalCapacity,
                SUM(CASE WHEN sec.isGeneralAdmission = 0 THEN COALESCE(bl.blockedCnt, 0) ELSE 0 END) AS totalBlocked
              FROM performance_section_tiers pst
              JOIN sections sec ON sec.sectionID = pst.sectionID
              LEFT JOIN (
                SELECT b.performanceID, se.sectionID, COUNT(*) AS blockedCnt
                FROM blocked_seats b JOIN seats se ON se.seatID = b.seatID
                GROUP BY b.performanceID, se.sectionID
              ) bl ON bl.performanceID = pst.performanceID AND bl.sectionID = pst.sectionID
              GROUP BY pst.performanceID
            )
            """;

    private static final String PERF_SOLD_CTE =
            """
            perf_sold AS (
              SELECT performanceID, COUNT(*) AS soldCnt FROM tickets WHERE status = 'ACTIVE' GROUP BY performanceID
            )
            """;

    private volatile NounPhraseExtractor nounPhraseExtractor;

    @Override
    public List<List<String>> runReport(int reportNumber, List<String> params) {
        List<String> p = params == null ? List.of() : params;
        return switch (reportNumber) {
            case 1 -> r1(p);
            case 2 -> r2(p);
            case 3 -> r3(p);
            case 4 -> r4(p);
            case 5 -> r5(p);
            case 6 -> r6(p);
            case 7 -> r7(p);
            case 8 -> r8(p);
            case 9 -> r9(p);
            default -> throw new IllegalArgumentException("Unknown report number: " + reportNumber);
        };
    }

    @Override
    public List<String> headersFor(int reportNumber, List<String> params) {
        List<String> p = params == null ? List.of() : params;
        return switch (reportNumber) {
            case 1 -> r1Headers(p);
            case 2 -> r2Headers(p);
            case 3 -> r3Headers(p);
            case 4 -> List.of("userID", "customer", "city", "purchased", "listed", "listedRatio");
            case 5 -> r5Headers(p);
            case 6 -> r6Headers(p);
            case 7 -> r7Headers(p);
            case 8 -> r8Headers(p);
            case 9 -> List.of("event", "nounPhrase", "count");
            default -> List.of();
        };
    }

    

    
    private List<List<String>> r1(List<String> p) {
        String grain = arg(p, 0).trim().toLowerCase();
        boolean byVenue = grain.equals("venue");
        if (!byVenue && !grain.equals("city") && !grain.isEmpty()) {
            throw new IllegalArgumentException("R1 grain must be city or venue, got: " + arg(p, 0));
        }
        LocalDate dateFrom = LocalDate.parse(arg(p, 1));
        LocalDate dateTo = LocalDate.parse(arg(p, 2));
        String city = argOrNull(p, 3);
        Integer venueID = null;
        if (byVenue) {
            if (city == null) {
                throw new IllegalArgumentException("R1 venue mode requires a city.");
            }
            String venueRaw = arg(p, 4);
            if (venueRaw.isEmpty()) {
                throw new IllegalArgumentException("R1 venue mode requires a venueID.");
            }
            venueID = Integer.parseInt(venueRaw);
        }

        StringBuilder sql = new StringBuilder(
                "SELECT pa.city" + (byVenue ? ", v.venueName" : "")
                        + ", COUNT(t.ticketID) AS ticketsSold, COALESCE(SUM(t.faceValue), 0) AS grossRevenue "
                        + "FROM tickets t "
                        + "JOIN orders o ON o.orderID = t.orderID "
                        + "JOIN performances p ON p.performanceID = t.performanceID "
                        + "JOIN venues v ON v.venueID = p.venueID "
                        + "JOIN postal_areas pa ON pa.postalCode = v.postalCode "
                        + "WHERE DATE(o.orderTimestamp) BETWEEN ? AND ? ");
        List<Object> bind = new ArrayList<>(List.of(dateFrom, dateTo));
        if (city != null) {
            sql.append("AND pa.city = ? ");
            bind.add(city);
        }
        if (venueID != null) {
            sql.append("AND v.venueID = ? ");
            bind.add(venueID);
        }
        sql.append("GROUP BY pa.city").append(byVenue ? ", v.venueName " : " ")
                .append("ORDER BY grossRevenue DESC");

        return queryDynamic(sql.toString(), bind, rs -> {
            List<String> row = new ArrayList<>();
            row.add(rs.getString("city"));
            if (byVenue) {
                row.add(rs.getString("venueName"));
            }
            row.add(String.valueOf(rs.getInt("ticketsSold")));
            row.add(fmtMoney(rs.getBigDecimal("grossRevenue")));
            return row;
        });
    }

    private List<String> r1Headers(List<String> p) {
        boolean byVenue = "VENUE".equalsIgnoreCase(arg(p, 0));
        return byVenue
                ? List.of("city", "venue", "ticketsSold", "grossRevenue")
                : List.of("city", "ticketsSold", "grossRevenue");
    }

    

    
    private List<List<String>> r2(List<String> p) {
        String grain = arg(p, 0).isEmpty() ? "COUNTRY" : arg(p, 0).toUpperCase();
        boolean city = grain.equals("CITY") || grain.equals("VENUE");
        boolean venue = grain.equals("VENUE");

        StringBuilder select = new StringBuilder("SELECT sg.segmentName, g.genreName, pa.country");
        StringBuilder groupBy = new StringBuilder("GROUP BY sg.segmentName, g.genreName, pa.country");
        if (city) {
            select.append(", pa.city");
            groupBy.append(", pa.city");
        }
        if (venue) {
            select.append(", v.venueName");
            groupBy.append(", v.venueName");
        }
        select.append(", COUNT(DISTINCT e.eventID) AS eventsCnt, COUNT(DISTINCT p.performanceID) AS perfsCnt ");

        String sql = select
                + "FROM performances p "
                + "JOIN events e ON e.eventID = p.eventID "
                + "JOIN genres g ON g.genreID = e.genreID "
                + "JOIN segments sg ON sg.segmentID = g.segmentID "
                + "JOIN venues v ON v.venueID = p.venueID "
                + "JOIN postal_areas pa ON pa.postalCode = v.postalCode "
                + groupBy
                + " ORDER BY sg.segmentName, g.genreName";

        return queryDynamic(sql, List.of(), rs -> {
            List<String> row = new ArrayList<>();
            row.add(rs.getString("segmentName"));
            row.add(rs.getString("genreName"));
            row.add(rs.getString("country"));
            if (city) {
                row.add(rs.getString("city"));
            }
            if (venue) {
                row.add(rs.getString("venueName"));
            }
            row.add(String.valueOf(rs.getInt("eventsCnt")));
            row.add(String.valueOf(rs.getInt("perfsCnt")));
            return row;
        });
    }

    private List<String> r2Headers(List<String> p) {
        String grain = arg(p, 0).isEmpty() ? "COUNTRY" : arg(p, 0).toUpperCase();
        List<String> h = new ArrayList<>(List.of("segment", "genre", "country"));
        if (grain.equals("CITY") || grain.equals("VENUE")) {
            h.add("city");
        }
        if (grain.equals("VENUE")) {
            h.add("venue");
        }
        h.add("events");
        h.add("performances");
        return h;
    }

    

    
    private List<List<String>> r3(List<String> p) {
        String scope = arg(p, 0).isEmpty() ? "OVERALL" : arg(p, 0).toUpperCase();
        String dateFrom = argOrNull(p, 1);
        String dateTo = argOrNull(p, 2);
        boolean byCountry = scope.equals("COUNTRY") || scope.equals("CITY");
        boolean byCity = scope.equals("CITY");

        StringBuilder cte = new StringBuilder("SELECT u.userID AS organizerID, u.name AS organizerName");
        StringBuilder groupBy = new StringBuilder("GROUP BY u.userID, u.name");
        if (byCountry) {
            cte.append(", pa.country");
            groupBy.append(", pa.country");
        }
        if (byCity) {
            cte.append(", pa.city");
            groupBy.append(", pa.city");
        }
        cte.append(", COALESCE(SUM(t.faceValue), 0) AS revenue ");

        StringBuilder from = new StringBuilder(
                "FROM users u "
                        + "JOIN events e ON e.organizerID = u.userID "
                        + "JOIN performances p ON p.eventID = e.eventID "
                        + (byCountry
                                ? "JOIN venues v ON v.venueID = p.venueID "
                                        + "JOIN postal_areas pa ON pa.postalCode = v.postalCode "
                                : "")
                        + "JOIN tickets t ON t.performanceID = p.performanceID "
                        + "JOIN orders o ON o.orderID = t.orderID "
                        + "WHERE u.userType = 'ORGANIZER' ");
        List<Object> bind = new ArrayList<>();
        if (dateFrom != null) {
            from.append("AND DATE(o.orderTimestamp) >= ? ");
            bind.add(LocalDate.parse(dateFrom));
        }
        if (dateTo != null) {
            from.append("AND DATE(o.orderTimestamp) <= ? ");
            bind.add(LocalDate.parse(dateTo));
        }

        String partition = byCity ? "PARTITION BY country, city " : byCountry ? "PARTITION BY country " : "";
        String orderPrefix = byCity ? "country, city, " : byCountry ? "country, " : "";
        String sql = "WITH org_rev AS (" + cte + from + groupBy + ") "
                + "SELECT *, RANK() OVER (" + partition + "ORDER BY revenue DESC) AS rnk FROM org_rev "
                + "ORDER BY " + orderPrefix + "rnk";

        boolean fCountry = byCountry;
        boolean fCity = byCity;
        return queryDynamic(sql, bind, rs -> {
            List<String> row = new ArrayList<>();
            if (fCountry) {
                row.add(rs.getString("country"));
            }
            if (fCity) {
                row.add(rs.getString("city"));
            }
            row.add(String.valueOf(rs.getInt("rnk")));
            row.add(String.valueOf(rs.getInt("organizerID")));
            row.add(rs.getString("organizerName"));
            row.add(fmtMoney(rs.getBigDecimal("revenue")));
            return row;
        });
    }

    private List<String> r3Headers(List<String> p) {
        String scope = arg(p, 0).isEmpty() ? "OVERALL" : arg(p, 0).toUpperCase();
        List<String> h = new ArrayList<>();
        if (scope.equals("COUNTRY") || scope.equals("CITY")) {
            h.add("country");
        }
        if (scope.equals("CITY")) {
            h.add("city");
        }
        h.add("rank");
        h.add("organizerID");
        h.add("organizer");
        h.add("revenue");
        return h;
    }

    

    
    private List<List<String>> r4(List<String> p) {
        int windowDays = parseIntOrDefault(arg(p, 0), 365);
        String sql =
                """
                WITH purchases AS (
                  SELECT o.customerID, pa.city, t.ticketID
                  FROM tickets t
                  JOIN orders o ON o.orderID = t.orderID
                  JOIN performances p ON p.performanceID = t.performanceID
                  JOIN venues v ON v.venueID = p.venueID
                  JOIN postal_areas pa ON pa.postalCode = v.postalCode
                  WHERE o.orderTimestamp >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                ),
                purchase_counts AS (
                  SELECT customerID, city, COUNT(*) AS purchased FROM purchases GROUP BY customerID, city
                ),
                listed_counts AS (
                  SELECT pu.customerID, pu.city, COUNT(DISTINCT rl.ticketID) AS listed
                  FROM purchases pu
                  JOIN resale_listings rl ON rl.ticketID = pu.ticketID AND rl.sellerID = pu.customerID
                  GROUP BY pu.customerID, pu.city
                )
                SELECT u.userID, u.name, pc.city, pc.purchased, COALESCE(lc.listed, 0) AS listed
                FROM purchase_counts pc
                JOIN users u ON u.userID = pc.customerID
                LEFT JOIN listed_counts lc ON lc.customerID = pc.customerID AND lc.city = pc.city
                WHERE pc.purchased >= 10 AND (COALESCE(lc.listed, 0) / pc.purchased) > 0.5
                ORDER BY (COALESCE(lc.listed, 0) / pc.purchased) DESC, pc.purchased DESC
                """;
        return queryDynamic(sql, List.of(windowDays), rs -> {
            int purchased = rs.getInt("purchased");
            int listed = rs.getInt("listed");
            BigDecimal ratio = purchased == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(listed).divide(BigDecimal.valueOf(purchased), 2, RoundingMode.HALF_UP);
            return List.of(
                    String.valueOf(rs.getInt("userID")),
                    rs.getString("name"),
                    rs.getString("city"),
                    String.valueOf(purchased),
                    String.valueOf(listed),
                    ratio.toPlainString());
        });
    }

    

    
    private List<List<String>> r5(List<String> p) {
        String mode = normalizeMode(arg(p, 0), "OVERALL");
        LocalDate dateFrom = LocalDate.parse(arg(p, 1));
        LocalDate dateTo = LocalDate.parse(arg(p, 2));

        if (mode.equals("PER_CITY")) {
            String sql =
                    """
                    WITH order_city AS (
                      SELECT DISTINCT o.orderID, o.customerID, pa.city
                      FROM orders o
                      JOIN tickets t ON t.orderID = o.orderID
                      JOIN performances p ON p.performanceID = t.performanceID
                      JOIN venues v ON v.venueID = p.venueID
                      JOIN postal_areas pa ON pa.postalCode = v.postalCode
                      WHERE DATE(o.orderTimestamp) BETWEEN ? AND ?
                    ),
                    cust_city_counts AS (
                      SELECT customerID, city, COUNT(*) AS cnt FROM order_city GROUP BY customerID, city
                      HAVING COUNT(*) >= 2
                    )
                    SELECT ccc.city, ccc.cnt, u.userID, u.name,
                      RANK() OVER (PARTITION BY ccc.city ORDER BY ccc.cnt DESC) AS rnk
                    FROM cust_city_counts ccc JOIN users u ON u.userID = ccc.customerID
                    ORDER BY ccc.city, rnk
                    """;
            return queryDynamic(sql, List.of(dateFrom, dateTo), rs -> List.of(
                    rs.getString("city"),
                    String.valueOf(rs.getInt("rnk")),
                    String.valueOf(rs.getInt("userID")),
                    rs.getString("name"),
                    String.valueOf(rs.getInt("cnt"))));
        }

        String sql =
                """
                WITH cust_orders AS (
                  SELECT customerID, COUNT(*) AS cnt FROM orders
                  WHERE DATE(orderTimestamp) BETWEEN ? AND ? GROUP BY customerID
                )
                SELECT co.cnt, u.userID, u.name, RANK() OVER (ORDER BY co.cnt DESC) AS rnk
                FROM cust_orders co JOIN users u ON u.userID = co.customerID
                ORDER BY rnk
                """;
        return queryDynamic(sql, List.of(dateFrom, dateTo), rs -> List.of(
                String.valueOf(rs.getInt("rnk")),
                String.valueOf(rs.getInt("userID")),
                rs.getString("name"),
                String.valueOf(rs.getInt("cnt"))));
    }

    private List<String> r5Headers(List<String> p) {
        String mode = normalizeMode(arg(p, 0), "OVERALL");
        return mode.equals("PER_CITY")
                ? List.of("city", "rank", "userID", "customer", "orderCount")
                : List.of("rank", "userID", "customer", "orderCount");
    }

    

    
    private List<List<String>> r6(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "BOTH" : arg(p, 0).toUpperCase();
        int windowDays = parseIntOrDefault(arg(p, 1), 365);
        int limit = parseIntOrDefault(arg(p, 2), 10);
        boolean both = mode.equals("BOTH");

        List<List<String>> rows = new ArrayList<>();
        if (mode.equals("CUSTOMERS") || both) {
            String sql =
                    "SELECT u.userID, u.name, COUNT(*) AS cancelledCount "
                            + "FROM tickets t JOIN users u ON u.userID = t.currentOwnerID "
                            + "WHERE t.status = 'CANCELLED' AND t.cancelledAt >= DATE_SUB(NOW(), INTERVAL ? DAY) "
                            + "GROUP BY u.userID, u.name ORDER BY cancelledCount DESC LIMIT ?";
            rows.addAll(queryDynamic(sql, List.of(windowDays, limit), rs -> {
                List<String> row = new ArrayList<>();
                if (both) {
                    row.add("CUSTOMER");
                }
                row.add(String.valueOf(rs.getInt("userID")));
                row.add(rs.getString("name"));
                row.add(String.valueOf(rs.getInt("cancelledCount")));
                return row;
            }));
        }
        if (mode.equals("ORGANIZERS") || both) {
            String sql =
                    "SELECT u.userID, u.name, COUNT(*) AS cancelledCount "
                            + "FROM performances p JOIN events e ON e.eventID = p.eventID "
                            + "JOIN users u ON u.userID = e.organizerID "
                            + "WHERE p.status = 'CANCELLED' AND p.cancelledAt >= DATE_SUB(NOW(), INTERVAL ? DAY) "
                            + "GROUP BY u.userID, u.name ORDER BY cancelledCount DESC LIMIT ?";
            rows.addAll(queryDynamic(sql, List.of(windowDays, limit), rs -> {
                List<String> row = new ArrayList<>();
                if (both) {
                    row.add("ORGANIZER");
                }
                row.add(String.valueOf(rs.getInt("userID")));
                row.add(rs.getString("name"));
                row.add(String.valueOf(rs.getInt("cancelledCount")));
                return row;
            }));
        }
        return rows;
    }

    private List<String> r6Headers(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "BOTH" : arg(p, 0).toUpperCase();
        List<String> h = new ArrayList<>();
        if (mode.equals("BOTH")) {
            h.add("type");
        }
        h.add("userID");
        h.add("name");
        h.add("cancelledCount");
        return h;
    }

    

    
    private List<List<String>> r7(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "PERF" : arg(p, 0).toUpperCase();
        return switch (mode) {
            case "TIER" -> r7Tier(argOrNull(p, 1));
            case "MONTH" -> r7Month(arg(p, 1), argOrNull(p, 2));
            default -> r7Perf(argOrNull(p, 1));
        };
    }

    private List<List<String>> r7Perf(String performanceIdOrNull) {
        StringBuilder sql = new StringBuilder(
                "WITH " + PERF_CAPACITY_CTE + ", " + PERF_SOLD_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, pa.city,
                               (pc.totalCapacity - pc.totalBlocked) AS sellable, COALESCE(ps.soldCnt, 0) AS sold
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        JOIN perf_capacity pc ON pc.performanceID = p.performanceID
                        LEFT JOIN perf_sold ps ON ps.performanceID = p.performanceID
                        """);
        List<Object> bind = new ArrayList<>();
        if (performanceIdOrNull != null) {
            sql.append("WHERE p.performanceID = ? ");
            bind.add(Integer.parseInt(performanceIdOrNull));
        }
        sql.append("ORDER BY p.date DESC, p.performanceID DESC");
        return queryDynamic(sql.toString(), bind, rs -> {
            int sellable = rs.getInt("sellable");
            int sold = rs.getInt("sold");
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("title"),
                    rs.getString("city"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    pct(sold, sellable));
        });
    }

    private List<List<String>> r7Tier(String performanceIdOrNull) {
        StringBuilder sql = new StringBuilder(
                """
                WITH tier_capacity AS (
                  SELECT pst.performanceID, pt.tierID, pt.tierName,
                    SUM(CASE WHEN sec.isGeneralAdmission = 1 THEN sec.standingCapacity
                             ELSE (SELECT COUNT(*) FROM seats se WHERE se.sectionID = sec.sectionID) END) AS capacity,
                    SUM(CASE WHEN sec.isGeneralAdmission = 0 THEN COALESCE(bl.blockedCnt, 0) ELSE 0 END) AS blocked
                  FROM performance_section_tiers pst
                  JOIN sections sec ON sec.sectionID = pst.sectionID
                  JOIN price_tiers pt ON pt.tierID = pst.tierID
                  LEFT JOIN (
                    SELECT b.performanceID, se.sectionID, COUNT(*) AS blockedCnt
                    FROM blocked_seats b JOIN seats se ON se.seatID = b.seatID
                    GROUP BY b.performanceID, se.sectionID
                  ) bl ON bl.performanceID = pst.performanceID AND bl.sectionID = pst.sectionID
                  GROUP BY pst.performanceID, pt.tierID, pt.tierName
                ),
                tier_sold AS (
                  SELECT pst.performanceID, pt.tierID, COUNT(t.ticketID) AS soldCnt
                  FROM performance_section_tiers pst
                  JOIN price_tiers pt ON pt.tierID = pst.tierID
                  LEFT JOIN tickets t ON t.performanceID = pst.performanceID AND t.sectionID = pst.sectionID AND t.status = 'ACTIVE'
                  GROUP BY pst.performanceID, pt.tierID
                )
                SELECT tc.performanceID, tc.tierName, (tc.capacity - tc.blocked) AS sellable, COALESCE(ts.soldCnt, 0) AS sold
                FROM tier_capacity tc
                LEFT JOIN tier_sold ts ON ts.performanceID = tc.performanceID AND ts.tierID = tc.tierID
                """);
        List<Object> bind = new ArrayList<>();
        if (performanceIdOrNull != null) {
            sql.append("WHERE tc.performanceID = ? ");
            bind.add(Integer.parseInt(performanceIdOrNull));
        }
        sql.append("ORDER BY tc.performanceID, tc.tierName");
        return queryDynamic(sql.toString(), bind, rs -> {
            int sellable = rs.getInt("sellable");
            int sold = rs.getInt("sold");
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("tierName"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    pct(sold, sellable));
        });
    }

    private List<List<String>> r7Month(String yearMonth, String cityOrNull) {
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new IllegalArgumentException("month (YYYY-MM) is required for R7 MONTH mode.");
        }
        StringBuilder sql = new StringBuilder(
                "WITH " + PERF_CAPACITY_CTE + ", " + PERF_SOLD_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, pa.city,
                               (pc.totalCapacity - pc.totalBlocked) AS sellable, COALESCE(ps.soldCnt, 0) AS sold
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        JOIN perf_capacity pc ON pc.performanceID = p.performanceID
                        LEFT JOIN perf_sold ps ON ps.performanceID = p.performanceID
                        WHERE DATE_FORMAT(p.date, '%Y-%m') = ?
                        """);
        List<Object> bind = new ArrayList<>(List.of(yearMonth));
        if (cityOrNull != null) {
            sql.append("AND pa.city = ? ");
            bind.add(cityOrNull);
        }
        sql.append("ORDER BY p.date, p.performanceID");
        return queryDynamic(sql.toString(), bind, rs -> {
            int sellable = rs.getInt("sellable");
            int sold = rs.getInt("sold");
            String flag = "";
            if (sellable > 0) {
                if (sold >= sellable) {
                    flag = "SOLD_OUT";
                } else if ((double) sold / sellable < 0.25) {
                    flag = "LOW";
                }
            }
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("title"),
                    rs.getString("city"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    pct(sold, sellable),
                    flag);
        });
    }

    private List<String> r7Headers(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "PERF" : arg(p, 0).toUpperCase();
        return switch (mode) {
            case "TIER" -> List.of("performanceID", "tier", "sellable", "sold", "sellThroughPct");
            case "MONTH" -> List.of("performanceID", "event", "city", "sellable", "sold", "sellThroughPct", "flag");
            default -> List.of("performanceID", "event", "city", "sellable", "sold", "sellThroughPct");
        };
    }

    

    
    private List<List<String>> r8(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "STATS" : arg(p, 0).toUpperCase();
        if (mode.equals("TOP10")) {
            LocalDate from = LocalDate.parse(arg(p, 1));
            LocalDate to = LocalDate.parse(arg(p, 2));
            String sql =
                    "SELECT e.eventID, e.title, COUNT(*) AS listingVolume "
                            + "FROM events e JOIN performances p ON p.eventID = e.eventID "
                            + "JOIN tickets t ON t.performanceID = p.performanceID "
                            + "JOIN resale_listings rl ON rl.ticketID = t.ticketID "
                            + "WHERE DATE(rl.createdAt) BETWEEN ? AND ? "
                            + "GROUP BY e.eventID, e.title ORDER BY listingVolume DESC LIMIT 10";
            return queryDynamic(sql, List.of(from, to), rs -> List.of(
                    String.valueOf(rs.getInt("eventID")),
                    rs.getString("title"),
                    String.valueOf(rs.getInt("listingVolume"))));
        }

        String sql =
                """
                SELECT e.eventID, e.title,
                  SUM(CASE WHEN rl.status = 'SOLD' THEN 1 ELSE 0 END) AS soldCount,
                  AVG(CASE WHEN rl.status = 'SOLD' THEN (rl.listingPrice / t.faceValue - 1) ELSE NULL END) AS avgMarkup,
                  SUM(CASE WHEN rl.status = 'SOLD' AND ABS(rl.listingPrice - t.faceValue * e.resaleCapRatio) < 0.01
                      THEN 1 ELSE 0 END) AS atCapCount
                FROM events e
                JOIN performances p ON p.eventID = e.eventID
                JOIN tickets t ON t.performanceID = p.performanceID
                JOIN resale_listings rl ON rl.ticketID = t.ticketID
                GROUP BY e.eventID, e.title ORDER BY soldCount DESC
                """;
        return queryDynamic(sql, List.of(), rs -> {
            int soldCount = rs.getInt("soldCount");
            int atCapCount = rs.getInt("atCapCount");
            BigDecimal avgMarkup = rs.getBigDecimal("avgMarkup");
            String markupPct = avgMarkup == null
                    ? "n/a"
                    : avgMarkup.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()
                            + "%";
            String atCapPct = soldCount == 0 ? "n/a" : pct(atCapCount, soldCount);
            return List.of(
                    String.valueOf(rs.getInt("eventID")),
                    rs.getString("title"),
                    String.valueOf(soldCount),
                    markupPct,
                    atCapPct);
        });
    }

    private List<String> r8Headers(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "STATS" : arg(p, 0).toUpperCase();
        return mode.equals("TOP10")
                ? List.of("eventID", "event", "listingVolume")
                : List.of("eventID", "event", "resaleCount", "avgMarkupPct", "atCapPct");
    }

    

    
    private List<List<String>> r9(List<String> p) {
        String eventFilter = argOrNull(p, 0);
        StringBuilder sql = new StringBuilder(
                "SELECT e.eventID, e.title, r.commentText "
                        + "FROM reviews r "
                        + "JOIN performances p ON p.performanceID = r.performanceID "
                        + "JOIN events e ON e.eventID = p.eventID ");
        List<Object> bind = new ArrayList<>();
        if (eventFilter != null) {
            sql.append("WHERE e.eventID = ? ");
            bind.add(Integer.parseInt(eventFilter));
        }
        sql.append("ORDER BY e.eventID");

        Map<Integer, String> eventTitles = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> phraseCounts = new LinkedHashMap<>();
        NounPhraseExtractor extractor = extractor();

        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, bind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int eventID = rs.getInt("eventID");
                    String title = rs.getString("title");
                    String comment = rs.getString("commentText");
                    eventTitles.putIfAbsent(eventID, title);
                    Map<String, Integer> counts = phraseCounts.computeIfAbsent(eventID, k -> new LinkedHashMap<>());
                    for (String phrase : extractor.extractNounPhrases(comment)) {
                        String cleaned = phrase == null ? "" : phrase.trim();
                        if (cleaned.length() < 2) {
                            continue;
                        }
                        counts.merge(cleaned, 1, Integer::sum);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("r9 (noun phrases) failed: " + e.getMessage(), e);
        }

        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Integer>> entry : phraseCounts.entrySet()) {
            String title = eventTitles.get(entry.getKey());
            entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(pe -> rows.add(List.of(title, pe.getKey(), String.valueOf(pe.getValue()))));
        }
        return rows;
    }

    private synchronized NounPhraseExtractor extractor() {
        if (nounPhraseExtractor == null) {
            nounPhraseExtractor = new OpenNlpNounPhraseExtractor();
        }
        return nounPhraseExtractor;
    }

    

    private static String normalizeMode(String raw, String defaultValue) {
        String v = raw == null || raw.isBlank() ? defaultValue : raw.trim().toUpperCase().replace('-', '_');
        return v;
    }

    private static String arg(List<String> params, int idx) {
        if (params == null || idx >= params.size()) {
            return "";
        }
        String v = params.get(idx);
        return v == null ? "" : v.trim();
    }

    private static String argOrNull(List<String> params, int idx) {
        String v = arg(params, idx);
        return v.isEmpty() ? null : v;
    }

    private static int parseIntOrDefault(String raw, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String pct(int numerator, int denominator) {
        if (denominator <= 0) {
            return "n/a";
        }
        BigDecimal ratio = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return ratio.toPlainString() + "%";
    }

    private static String fmtMoney(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @FunctionalInterface
    private interface RowMapper {
        List<String> map(ResultSet rs) throws SQLException;
    }

    private static List<List<String>> queryDynamic(String sql, List<Object> params, RowMapper mapper) {
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bindParams(ps, params);
            List<List<String>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("report query failed: " + e.getMessage(), e);
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
}
