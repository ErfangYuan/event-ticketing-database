package mytix.database.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mytix.database.DatabaseLogistics;
import mytix.database.query.ScalperPolicy;

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

    @Override
    public List<List<String>> runReport(int reportNumber, List<String> params) {
        List<String> p = params == null ? List.of() : params;
        validateParameters(reportNumber, p);
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
            case 4 -> List.of("userID", "customer", "country", "city", "purchased", "listed", "listedRatio");
            case 5 -> r5Headers(p);
            case 6 -> r6Headers(p);
            case 7 -> r7Headers(p);
            case 8 -> r8Headers(p);
            case 9 -> List.of("event", "nounPhrase", "count");
            default -> List.of();
        };
    }

    

    
    private List<List<String>> r1(List<String> p) {
        String grain = arg(p, 0).trim().toLowerCase(Locale.ROOT);
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
                "SELECT pa.country, pa.city" + (byVenue ? ", v.venueID, v.venueName" : "")
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
        sql.append("GROUP BY pa.country, pa.city").append(byVenue ? ", v.venueID, v.venueName " : " ")
                .append("ORDER BY grossRevenue DESC, pa.country, pa.city")
                .append(byVenue ? ", v.venueID" : "");

        return queryDynamic(sql.toString(), bind, rs -> {
            List<String> row = new ArrayList<>();
            row.add(rs.getString("country"));
            row.add(rs.getString("city"));
            if (byVenue) {
                row.add(String.valueOf(rs.getInt("venueID")));
                row.add(rs.getString("venueName"));
            }
            row.add(String.valueOf(rs.getLong("ticketsSold")));
            row.add(fmtMoney(rs.getBigDecimal("grossRevenue")));
            return row;
        });
    }

    private List<String> r1Headers(List<String> p) {
        boolean byVenue = "VENUE".equalsIgnoreCase(arg(p, 0));
        return byVenue
                ? List.of("country", "city", "venueID", "venue", "ticketsSold", "grossRevenue")
                : List.of("country", "city", "ticketsSold", "grossRevenue");
    }

    

    
    private List<List<String>> r2(List<String> p) {
        String grain = arg(p, 0).isEmpty() ? "COUNTRY" : arg(p, 0).toUpperCase(Locale.ROOT);
        boolean city = grain.equals("CITY") || grain.equals("VENUE");
        boolean venue = grain.equals("VENUE");

        StringBuilder select = new StringBuilder("SELECT sg.segmentName, g.genreName, pa.country");
        StringBuilder groupBy = new StringBuilder("GROUP BY sg.segmentName, g.genreName, pa.country");
        if (city) {
            select.append(", pa.city");
            groupBy.append(", pa.city");
        }
        if (venue) {
            select.append(", v.venueID, v.venueName");
            groupBy.append(", v.venueID, v.venueName");
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
                + " ORDER BY sg.segmentName, g.genreName, pa.country"
                + (city ? ", pa.city" : "") + (venue ? ", v.venueID" : "");

        return queryDynamic(sql, List.of(), rs -> {
            List<String> row = new ArrayList<>();
            row.add(rs.getString("segmentName"));
            row.add(rs.getString("genreName"));
            row.add(rs.getString("country"));
            if (city) {
                row.add(rs.getString("city"));
            }
            if (venue) {
                row.add(String.valueOf(rs.getInt("venueID")));
                row.add(rs.getString("venueName"));
            }
            row.add(String.valueOf(rs.getInt("eventsCnt")));
            row.add(String.valueOf(rs.getInt("perfsCnt")));
            return row;
        });
    }

    private List<String> r2Headers(List<String> p) {
        String grain = arg(p, 0).isEmpty() ? "COUNTRY" : arg(p, 0).toUpperCase(Locale.ROOT);
        List<String> h = new ArrayList<>(List.of("segment", "genre", "country"));
        if (grain.equals("CITY") || grain.equals("VENUE")) {
            h.add("city");
        }
        if (grain.equals("VENUE")) {
            h.add("venueID");
            h.add("venue");
        }
        h.add("events");
        h.add("performances");
        return h;
    }

    

    
    private List<List<String>> r3(List<String> p) {
        String scope = arg(p, 0).isEmpty() ? "OVERALL" : arg(p, 0).toUpperCase(Locale.ROOT);
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
                + "ORDER BY " + orderPrefix + "rnk, organizerID";

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
        String scope = arg(p, 0).isEmpty() ? "OVERALL" : arg(p, 0).toUpperCase(Locale.ROOT);
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
        boolean annual = arg(p, 0).isEmpty();
        String lowerBound = annual ? ScalperPolicy.ANNUAL_BOUND : "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? DAY)";
        List<Object> bind = annual ? List.of() : List.of(positiveInteger(arg(p, 0), "window days", null));
        String sql = "SELECT u.userID,u.name,co.country,co.city,co.purchased,co.listed,"
                + "co.listed/co.purchased AS listedRatio FROM (" + ScalperPolicy.cohortSql(lowerBound) + ") co "
                + "JOIN users u ON u.userID=co.customerID WHERE co.purchased>=10 AND co.listed*2>co.purchased "
                + "ORDER BY listedRatio DESC,co.purchased DESC,co.country,co.city,u.userID";
        return queryDynamic(sql, bind, rs -> List.of(
                    String.valueOf(rs.getInt("userID")),
                    rs.getString("name"),
                    rs.getString("country"),
                    rs.getString("city"),
                    String.valueOf(rs.getLong("purchased")),
                    String.valueOf(rs.getLong("listed")),
                    fmtMoney(rs.getBigDecimal("listedRatio"))));
    }

    

    
    private List<List<String>> r5(List<String> p) {
        String mode = normalizeMode(arg(p, 0), "OVERALL");
        LocalDate dateFrom = LocalDate.parse(arg(p, 1));
        LocalDate dateTo = LocalDate.parse(arg(p, 2));

        if (mode.equals("PER_CITY")) {
            String sql =
                    """
                    WITH order_city AS (
                      SELECT o.orderID, o.customerID, o.orderTimestamp, pa.country, pa.city
                      FROM orders o
                      JOIN performances p ON p.performanceID = o.performanceID
                      JOIN venues v ON v.venueID = p.venueID
                      JOIN postal_areas pa ON pa.postalCode = v.postalCode
                    ),
                    annual_eligible AS (
                      SELECT customerID,country,city FROM order_city
                      WHERE orderTimestamp>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 YEAR)
                        AND orderTimestamp<=CURRENT_TIMESTAMP
                      GROUP BY customerID,country,city
                      HAVING COUNT(*) >= 2
                    ),
                    cust_city_counts AS (
                      SELECT oc.customerID,oc.country,oc.city,COUNT(*) AS cnt FROM order_city oc
                      JOIN annual_eligible ae ON ae.customerID=oc.customerID AND ae.country=oc.country AND ae.city=oc.city
                      WHERE DATE(oc.orderTimestamp) BETWEEN ? AND ?
                      GROUP BY oc.customerID,oc.country,oc.city
                    )
                    SELECT ccc.country,ccc.city,ccc.cnt,u.userID,u.name,
                      RANK() OVER (PARTITION BY ccc.country,ccc.city ORDER BY ccc.cnt DESC) AS rnk
                    FROM cust_city_counts ccc JOIN users u ON u.userID = ccc.customerID
                    ORDER BY ccc.country,ccc.city,rnk,u.userID
                    """;
            return queryDynamic(sql, List.of(dateFrom, dateTo), rs -> List.of(
                    rs.getString("country"),
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
                ORDER BY rnk,u.userID
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
                ? List.of("country", "city", "rank", "userID", "customer", "orderCount")
                : List.of("rank", "userID", "customer", "orderCount");
    }

    

    
    private List<List<String>> r6(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "BOTH" : arg(p, 0).toUpperCase(Locale.ROOT);
        int limit = parseIntOrDefault(arg(p, 2), 10);
        boolean annual = arg(p, 1).isEmpty();
        String lowerBound = annual ? ScalperPolicy.ANNUAL_BOUND : "DATE_SUB(CURRENT_TIMESTAMP,INTERVAL ? DAY)";
        List<Object> bind = new ArrayList<>();
        if (!annual) {
            bind.add(positiveInteger(arg(p, 1), "window days", null));
        }
        bind.add(limit);
        boolean both = mode.equals("BOTH");

        List<List<String>> rows = new ArrayList<>();
        if (mode.equals("CUSTOMERS") || both) {
            String sql =
                    "SELECT u.userID, u.name, COUNT(*) AS cancelledCount "
                            + "FROM tickets t JOIN users u ON u.userID = t.cancelledBy "
                            + "WHERE t.cancellationType = 'CUSTOMER' AND t.cancelledAt >= " + lowerBound
                            + " AND t.cancelledAt<=CURRENT_TIMESTAMP "
                            + "GROUP BY u.userID, u.name ORDER BY cancelledCount DESC,u.userID LIMIT ?";
            rows.addAll(queryDynamic(sql, bind, rs -> {
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
                            + "WHERE p.status = 'CANCELLED' AND p.cancelledAt >= " + lowerBound
                            + " AND p.cancelledAt<=CURRENT_TIMESTAMP "
                            + "GROUP BY u.userID, u.name ORDER BY cancelledCount DESC,u.userID LIMIT ?";
            rows.addAll(queryDynamic(sql, bind, rs -> {
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
        String mode = arg(p, 0).isEmpty() ? "BOTH" : arg(p, 0).toUpperCase(Locale.ROOT);
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
        String mode = arg(p, 0).isEmpty() ? "PERF" : arg(p, 0).toUpperCase(Locale.ROOT);
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
                               (pc.totalCapacity - pc.totalBlocked) AS sellable, COALESCE(ps.soldCnt, 0) AS sold,
                               100.0 * COALESCE(ps.soldCnt, 0) / NULLIF(pc.totalCapacity - pc.totalBlocked, 0) AS sellThroughPct
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
            long sellable = rs.getLong("sellable");
            long sold = rs.getLong("sold");
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("title"),
                    rs.getString("city"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    fmtPercent(rs.getBigDecimal("sellThroughPct")));
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
                SELECT tc.performanceID, tc.tierName, (tc.capacity - tc.blocked) AS sellable, COALESCE(ts.soldCnt, 0) AS sold,
                       100.0 * COALESCE(ts.soldCnt, 0) / NULLIF(tc.capacity - tc.blocked, 0) AS sellThroughPct
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
            long sellable = rs.getLong("sellable");
            long sold = rs.getLong("sold");
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("tierName"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    fmtPercent(rs.getBigDecimal("sellThroughPct")));
        });
    }

    private List<List<String>> r7Month(String yearMonth, String cityOrNull) {
        YearMonth month = YearMonth.parse(yearMonth);
        StringBuilder sql = new StringBuilder(
                "WITH " + PERF_CAPACITY_CTE + ", " + PERF_SOLD_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, pa.city,
                               (pc.totalCapacity - pc.totalBlocked) AS sellable, COALESCE(ps.soldCnt, 0) AS sold,
                               100.0 * COALESCE(ps.soldCnt, 0) / NULLIF(pc.totalCapacity - pc.totalBlocked, 0) AS sellThroughPct,
                               CASE WHEN COALESCE(ps.soldCnt, 0) = (pc.totalCapacity - pc.totalBlocked)
                                    THEN 'SOLD_OUT' ELSE 'LOW' END AS flag
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        JOIN perf_capacity pc ON pc.performanceID = p.performanceID
                        LEFT JOIN perf_sold ps ON ps.performanceID = p.performanceID
                        WHERE p.date >= ? AND p.date <= ? AND p.status = 'SCHEDULED'
                          AND (pc.totalCapacity - pc.totalBlocked) > 0
                          AND (COALESCE(ps.soldCnt, 0) = (pc.totalCapacity - pc.totalBlocked)
                               OR COALESCE(ps.soldCnt, 0) * 4 < (pc.totalCapacity - pc.totalBlocked))
                        """);
        List<Object> bind = new ArrayList<>(List.of(month.atDay(1), month.atEndOfMonth()));
        if (cityOrNull != null) {
            sql.append("AND pa.city = ? ");
            bind.add(cityOrNull);
        }
        sql.append("ORDER BY p.date, p.performanceID");
        return queryDynamic(sql.toString(), bind, rs -> {
            long sellable = rs.getLong("sellable");
            long sold = rs.getLong("sold");
            return List.of(
                    String.valueOf(rs.getInt("performanceID")),
                    rs.getString("title"),
                    rs.getString("city"),
                    String.valueOf(sellable),
                    String.valueOf(sold),
                    fmtPercent(rs.getBigDecimal("sellThroughPct")),
                    rs.getString("flag"));
        });
    }

    private List<String> r7Headers(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "PERF" : arg(p, 0).toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "TIER" -> List.of("performanceID", "tier", "sellable", "sold", "sellThroughPct");
            case "MONTH" -> List.of("performanceID", "event", "city", "sellable", "sold", "sellThroughPct", "flag");
            default -> List.of("performanceID", "event", "city", "sellable", "sold", "sellThroughPct");
        };
    }

    

    
    private List<List<String>> r8(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "STATS" : arg(p, 0).toUpperCase(Locale.ROOT);
        if (mode.equals("TOP10")) {
            LocalDate from = LocalDate.parse(arg(p, 1));
            LocalDate to = LocalDate.parse(arg(p, 2));
            String sql =
                    "SELECT e.eventID, e.title, COUNT(*) AS resaleCount "
                            + "FROM events e JOIN performances p ON p.eventID = e.eventID "
                            + "JOIN tickets t ON t.performanceID = p.performanceID "
                            + "JOIN resale_listings rl ON rl.ticketID = t.ticketID "
                            + "WHERE rl.status = 'SOLD' AND DATE(rl.soldAt) BETWEEN ? AND ? "
                            + "GROUP BY e.eventID, e.title ORDER BY resaleCount DESC, e.eventID LIMIT 10";
            return queryDynamic(sql, List.of(from, to), rs -> List.of(
                    String.valueOf(rs.getInt("eventID")),
                    rs.getString("title"),
                    String.valueOf(rs.getLong("resaleCount"))));
        }

        String sql =
                """
                SELECT e.eventID, e.title,
                  SUM(CASE WHEN rl.status = 'SOLD' THEN 1 ELSE 0 END) AS soldCount,
                  100.0 * AVG(CASE WHEN rl.status = 'SOLD' THEN (rl.listingPrice / NULLIF(t.faceValue, 0) - 1) ELSE NULL END) AS avgMarkupPct,
                  SUM(CASE WHEN rl.listingPrice = ROUND(t.faceValue * rl.capRatio, 2)
                      THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(rl.listingID), 0) AS atCapPct
                FROM events e
                LEFT JOIN performances p ON p.eventID = e.eventID
                LEFT JOIN tickets t ON t.performanceID = p.performanceID
                LEFT JOIN resale_listings rl ON rl.ticketID = t.ticketID
                GROUP BY e.eventID, e.title ORDER BY soldCount DESC, e.eventID
                """;
        return queryDynamic(sql, List.of(), rs -> {
            long soldCount = rs.getLong("soldCount");
            return List.of(
                    String.valueOf(rs.getInt("eventID")),
                    rs.getString("title"),
                    String.valueOf(soldCount),
                    fmtPercent(rs.getBigDecimal("avgMarkupPct")),
                    fmtPercent(rs.getBigDecimal("atCapPct")));
        });
    }

    private List<String> r8Headers(List<String> p) {
        String mode = arg(p, 0).isEmpty() ? "STATS" : arg(p, 0).toUpperCase(Locale.ROOT);
        return mode.equals("TOP10")
                ? List.of("eventID", "event", "resaleCount")
                : List.of("eventID", "event", "resaleCount", "avgMarkupPct", "atCapPct");
    }

    

    
    private List<List<String>> r9(List<String> p) {
        String eventFilter = argOrNull(p, 0);
        StringBuilder sql = new StringBuilder(
                """
                WITH phrase_counts AS (
                  SELECT p.eventID,np.nounPhrase,COUNT(*) AS occurrences
                  FROM review_noun_phrases np
                  JOIN reviews r ON r.reviewID=np.reviewID
                  JOIN performances p ON p.performanceID=r.performanceID
                """);
        List<Object> bind = new ArrayList<>();
        if (eventFilter != null) {
            sql.append("WHERE p.eventID = ? ");
            bind.add(Integer.parseInt(eventFilter));
        }
        sql.append("""
                  GROUP BY p.eventID,np.nounPhrase
                ), ranked AS (
                  SELECT eventID,nounPhrase,occurrences,
                         ROW_NUMBER() OVER (PARTITION BY eventID ORDER BY occurrences DESC,nounPhrase) AS phraseRank
                  FROM phrase_counts
                )
                SELECT /*+ SET_VAR(max_sort_length=65535) SET_VAR(sort_buffer_size=1048576) */
                       e.title,r.nounPhrase,r.occurrences
                FROM ranked r JOIN events e ON e.eventID=r.eventID
                WHERE r.phraseRank<=10 ORDER BY e.eventID,r.phraseRank
                """);
        // TEXT comparisons must include the entire possible 65535-byte phrase;
        // statement-scoped hints avoid changing persistent/session configuration.
        return queryDynamic(sql.toString(), bind, rs -> List.of(
                rs.getString("title"), rs.getString("nounPhrase"), String.valueOf(rs.getLong("occurrences"))));
    }

    

    private static String normalizeMode(String raw, String defaultValue) {
        String v = raw == null || raw.isBlank() ? defaultValue : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return v;
    }

    private static void validateParameters(int report, List<String> p) {
        switch (report) {
            case 1 -> {
                String mode = requireMode(arg(p, 0), "CITY", "CITY", "VENUE");
                requireDateRange(arg(p, 1), arg(p, 2), false);
                if (mode.equals("VENUE")) {
                    if (arg(p, 3).isEmpty()) {
                        throw new IllegalStateException("R1 venue mode requires a city.");
                    }
                    positiveInteger(arg(p, 4), "venueID", null);
                }
            }
            case 2 -> requireMode(arg(p, 0), "COUNTRY", "COUNTRY", "CITY", "VENUE");
            case 3 -> {
                requireMode(arg(p, 0), "OVERALL", "OVERALL", "COUNTRY", "CITY");
                requireDateRange(arg(p, 1), arg(p, 2), true);
            }
            case 4 -> positiveInteger(arg(p, 0), "window days", 365);
            case 5 -> {
                requireMode(arg(p, 0), "OVERALL", "OVERALL", "PER_CITY");
                requireDateRange(arg(p, 1), arg(p, 2), false);
            }
            case 6 -> {
                requireMode(arg(p, 0), "BOTH", "CUSTOMERS", "ORGANIZERS", "BOTH");
                positiveInteger(arg(p, 1), "window days", 365);
                positiveInteger(arg(p, 2), "limit", 10);
            }
            case 7 -> {
                String mode = requireMode(arg(p, 0), "PERF", "PERF", "TIER", "MONTH");
                if (mode.equals("MONTH")) {
                    String value = arg(p, 1);
                    try {
                        if (!value.matches("\\d{4}-\\d{2}")) {
                            throw new DateTimeParseException("Invalid month", value, 0);
                        }
                        YearMonth.parse(value);
                    } catch (DateTimeParseException ex) {
                        throw new IllegalStateException("Invalid month: use a real calendar month in YYYY-MM format.", ex);
                    }
                } else if (!arg(p, 1).isEmpty()) {
                    positiveInteger(arg(p, 1), "performanceID", null);
                }
            }
            case 8 -> {
                String mode = requireMode(arg(p, 0), "STATS", "STATS", "TOP10");
                if (mode.equals("TOP10")) {
                    requireDateRange(arg(p, 1), arg(p, 2), false);
                }
            }
            case 9 -> {
                if (!arg(p, 0).isEmpty()) {
                    positiveInteger(arg(p, 0), "eventID", null);
                }
            }
            default -> throw new IllegalStateException("Unknown report number: " + report);
        }
    }

    private static String requireMode(String raw, String defaultValue, String... allowed) {
        String mode = normalizeMode(raw, defaultValue);
        if (!List.of(allowed).contains(mode)) {
            throw new IllegalStateException("Invalid report mode: " + raw);
        }
        return mode;
    }

    private static void requireDateRange(String from, String to, boolean optional) {
        LocalDate first = reportDate(from, "dateFrom", optional);
        LocalDate last = reportDate(to, "dateTo", optional);
        if (first != null && last != null && first.isAfter(last)) {
            throw new IllegalStateException("Invalid date range: dateFrom must not be after dateTo.");
        }
    }

    private static LocalDate reportDate(String raw, String label, boolean optional) {
        if (optional && raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("Invalid " + label + ": use a real calendar date in YYYY-MM-DD format.", ex);
        }
    }

    private static int positiveInteger(String raw, String label, Integer defaultValue) {
        if (raw.isBlank() && defaultValue != null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The same validation message covers non-integers and overflow.
        }
        throw new IllegalStateException("Invalid " + label + ": a positive integer is required.");
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
        return positiveInteger(raw == null ? "" : raw.trim(), "value", def);
    }

    private static String fmtPercent(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
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
