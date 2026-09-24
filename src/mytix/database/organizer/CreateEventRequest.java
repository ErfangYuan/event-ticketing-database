package mytix.database.organizer;

import java.math.BigDecimal;
import java.util.List;

public record CreateEventRequest(
        int organizerID,
        int genreID,
        String title,
        BigDecimal resaleCapRatio,
        List<Integer> artistIDsInBillingOrder) {}
