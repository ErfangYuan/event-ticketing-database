package mytix.database.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface QueryService {

    List<List<String>> q1Vicinity(
            double lat, double lng, double radiusKm, String rankBy);

    List<List<String>> q2PostalAdjacent(String postalCode);

    List<List<String>> q3ExactAddress(String address);

    List<List<String>> q4TemporalAvailability(
            LocalDate from, LocalDate to, int minAvailable);

    List<List<String>> q5Combined(Q5Filter filter);

    List<List<String>> q6SeatMapSummary(int performanceID);

    List<List<String>> q7BestConsecutive(
            int performanceID, int quantity, BigDecimal budgetOrNull);
}
