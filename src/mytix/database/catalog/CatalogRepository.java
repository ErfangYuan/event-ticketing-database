package mytix.database.catalog;

import java.util.List;

public interface CatalogRepository {

    List<List<String>> listSegments();

    List<List<String>> listGenres();

    List<List<String>> listVenues();

    List<List<String>> listVenuesByCity(String city);

    List<List<String>> listEvents(Integer organizerIDOrNull);

    List<List<String>> listArtists();

    
    List<List<String>> listUpcomingEvents();

    
    List<List<String>> listUpcomingPerformances(int eventID);

    List<List<String>> listSectionsForVenue(int venueID);

    
    List<List<String>> listAvailableReservedSeats(int performanceID);

    
    List<List<String>> listSeatMapPreview(int performanceID);

    
    List<List<String>> listCustomerTickets(int customerID);

    
    List<List<String>> ticketDetail(int ticketID);

    List<List<String>> listActiveResaleListings(int excludeSellerID);

    List<List<String>> listActiveListingsForSeller(int customerID);

    
    List<List<String>> listEligibleReviewPerformances(int customerID);

    
    Integer eventGenreID(int eventID);

    
    List<List<String>> listMyPerformances(int organizerID);

    
    List<List<String>> listTiers(int performanceID);

    
    List<List<String>> listBlockedSeats(int performanceID);
}
