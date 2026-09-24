package mytix.database.transaction;

import java.util.List;

public record BookRequest(
        int customerID,
        int performanceID,
        List<Integer> seatIDs,
        Integer gaSectionID,
        int gaQuantity) {}
