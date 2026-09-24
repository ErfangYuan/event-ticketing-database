package mytix.database.transaction;

public interface BookingService {

    
    int bookTickets(BookRequest request);

    boolean cancelTicket(int customerID, int ticketID);

    int listForResale(int customerID, int ticketID, java.math.BigDecimal askingPrice);

    boolean withdrawListing(int customerID, int listingID);

    int buyResaleListing(int buyerID, int listingID);

    void writeReview(
            int customerID,
            int performanceID,
            int eventRating,
            int venueRating,
            String comment);
}
