package mytix.database.organizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface OrganizerService {

    int createEvent(CreateEventRequest request);

    
    List<List<String>> suggestPricing(int venueID, int genreID, LocalDate date);

    
    double estimateDeltaRevenue(
            double suggestedPrice, double tierCapacityUnits, double sellThrough, double delta);

    
    int targetCapacity(int venueID);

    int createPerformance(CreatePerformanceRequest request);

    boolean updateTierPrice(int organizerID, int tierID, BigDecimal newPrice);

    boolean blockSeat(int organizerID, int performanceID, int seatID, String reason);

    boolean unblockSeat(int organizerID, int performanceID, int seatID);

    boolean cancelPerformance(int organizerID, int performanceID);
}
