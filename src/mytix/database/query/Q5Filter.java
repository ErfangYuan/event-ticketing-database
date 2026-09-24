package mytix.database.query;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Q5Filter(
        String city,
        String segmentName,
        String genreName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minAvailable,
        String sectionType) {

    
    public boolean reservedOnly() {
        return "RESERVED".equalsIgnoreCase(sectionType);
    }

    public boolean gaOnly() {
        return "GA".equalsIgnoreCase(sectionType);
    }
}
