package mytix.database.organizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record CreatePerformanceRequest(
        int organizerID,
        int eventID,
        int venueID,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        
        Map<String, BigDecimal> tierPrices,
        
        Map<Integer, String> sectionToTier) {}
