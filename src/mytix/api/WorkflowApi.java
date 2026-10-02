package mytix.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import mytix.database.catalog.*;
import mytix.database.query.*;
import mytix.database.report.*;
import mytix.database.organizer.*;
import mytix.database.transaction.*;
import static mytix.api.ApiData.*;

final class WorkflowApi {
    final JdbcQueryService queries=new JdbcQueryService();
    final JdbcReportService reports=new JdbcReportService();
    final JdbcBookingService booking=new JdbcBookingService();
    final JdbcOrganizerService organizer=new JdbcOrganizerService();
    final JdbcCatalogRepository catalog=new JdbcCatalogRepository();
    static Table table(String headers,List<List<String>> rows){return new Table(List.of(headers.split(",")),rows);}
    Object query(int n,JsonNode b) {
        return switch(n) {
            case 1 -> table("performanceID,event,venue,city,distanceKm,cheapest",queries.q1Vicinity(number(b,"latitude"),number(b,"longitude"),number(b,"radiusKm"),text(b,"rankBy")));
            case 2 -> table("performanceID,event,venue,city,postalCode",queries.q2PostalAdjacent(required(b,"postalCode")));
            case 3 -> table("venueID,venueName,address,performanceID,title,date,startTime",queries.q3ExactAddress(required(b,"address")));
            case 4 -> {
                String mode=text(b,"geography");GeographicFilter filter=switch(mode) {
                    case "VICINITY" -> GeographicFilter.vicinity(number(b,"latitude"),number(b,"longitude"),number(b,"radiusKm"),text(b,"rankBy"));
                    case "POSTAL" -> GeographicFilter.postal(required(b,"postalCode"));
                    case "ADDRESS" -> GeographicFilter.address(required(b,"address"));
                    case "ALL","" -> GeographicFilter.all();
                    default -> throw new Failure(400,"Unknown geographic mode.");
                };
                yield table("performanceID,title,venue,city,date,startTime,available",queries.q4TemporalAvailability(date(b,"from",false),date(b,"to",false),integer(b,"minAvailable",1),filter));
            }
            case 5 -> table("performanceID,event,genre,city,date,available,cheapest",queries.q5Combined(new Q5Filter(text(b,"city"),text(b,"segmentName"),text(b,"genreName"),date(b,"from",true),date(b,"to",true),money(b,"minPrice",true),money(b,"maxPrice",true),integer(b,"minAvailable",0),text(b,"sectionType").equals("ANY")?"":text(b,"sectionType"))));
            case 6 -> table("section,tier,price,available,sold,blocked",queries.q6SeatMapSummary(id(b,"performanceID")));
            case 7 -> table("section,row,fromSeat,toSeat,totalPrice",queries.q7BestConsecutive(id(b,"performanceID"),id(b,"quantity"),money(b,"budget",true)));
            default -> throw new Failure(404,"Unknown query.");
        };
    }
    Object report(int n,JsonNode b) {
        List<String> params=new ArrayList<>();JsonNode p=b.path("params");
        if(!p.isArray()||p.size()>6)throw new Failure(400,"Report parameters must be an array.");
        for(JsonNode v:p)params.add(v.asText());
        return new Table(reports.headersFor(n,params),reports.runReport(n,params));
    }
    Object catalog(String name,JsonNode b,Actor a) {
        return switch(name) {
            case "events" -> table("eventID,event,genre,segment",catalog.listUpcomingEvents());
            case "performances" -> table("performanceID,date,startTime,endTime,venue,city",catalog.listUpcomingPerformances(id(b,"eventID")));
            case "seats" -> table("seatID,section,seat,tier,price",catalog.listAvailableReservedSeats(id(b,"performanceID")));
            case "seat-map" -> table("sectionID,section,kind,tier,price,available,sold,blocked",catalog.listSeatMapPreview(id(b,"performanceID")));
            case "tickets" -> table("ticketID,event,date,section,seat,status,faceValue",catalog.listCustomerTickets(role(a,"CUSTOMER").id()));
            case "history" -> table("orderID,ticketID,event,date,section,seat,status,paid,ownership",catalog.listCustomerPurchaseHistory(role(a,"CUSTOMER").id()));
            case "ticket" -> table("ticketID,ownerships,activeListing",catalog.ticketDetail(role(a,"CUSTOMER").id(),id(b,"ticketID")));
            case "resale" -> table("listingID,event,date,section,seat,faceValue,listingPrice,sellerID",catalog.listActiveResaleListings(a==null?-1:a.id()));
            case "my-listings" -> table("listingID,ticketID,event,date,section,seat,listingPrice",catalog.listActiveListingsForSeller(role(a,"CUSTOMER").id()));
            case "reviewable" -> table("performanceID,event,venue,date",catalog.listEligibleReviewPerformances(role(a,"CUSTOMER").id()));
            case "my-performances" -> table("performanceID,event,venue,date,startTime,status",catalog.listMyPerformances(role(a,"ORGANIZER").id()));
            case "my-events" -> table("eventID,event,genre",catalog.listEvents(role(a,"ORGANIZER").id()));
            case "tiers" -> table("tierID,tier,price,soldCount",catalog.listTiers(id(b,"performanceID")));
            case "blocked" -> table("seatID,section,seat,reason",catalog.listBlockedSeats(id(b,"performanceID")));
            default -> throw new Failure(404,"Unknown catalog view.");
        };
    }
    Object action(String name,JsonNode b,Actor a)throws Exception {
        if(name.startsWith("customer/"))role(a,"CUSTOMER");else role(a,"ORGANIZER");
        return switch(name) {
            case "customer/book" -> ok(booking.bookTickets(new BookRequest(a.id(),id(b,"performanceID"),ids(b,"seatIDs"),integer(b,"gaSectionID",0)==0?null:id(b,"gaSectionID"),integer(b,"gaQuantity",0))));
            case "customer/cancel" -> changed(booking.cancelTicket(a.id(),id(b,"ticketID")));
            case "customer/list" -> ok(booking.listForResale(a.id(),id(b,"ticketID"),money(b,"askingPrice",false)));
            case "customer/withdraw" -> changed(booking.withdrawListing(a.id(),id(b,"listingID")));
            case "customer/buy" -> ok(booking.buyResaleListing(a.id(),id(b,"listingID")));
            case "customer/review" -> {booking.writeReview(a.id(),id(b,"performanceID"),id(b,"eventRating"),id(b,"venueRating"),required(b,"comment"));yield ok(true);}
            case "organizer/event" -> ok(organizer.createEvent(new CreateEventRequest(a.id(),id(b,"genreID"),required(b,"title"),money(b,"resaleCapRatio",false),ids(b,"artistIDs"))));
            case "organizer/performance" -> {
                Map<String,BigDecimal> tiers=new LinkedHashMap<>();Map<Integer,String> mapping=new LinkedHashMap<>();
                JsonNode t=object(b,"tierPrices"),s=object(b,"sectionToTier");
                for(Iterator<String> it=t.fieldNames();it.hasNext();){String key=it.next();tiers.put(key,new BigDecimal(t.get(key).asText()));}
                for(Iterator<String> it=s.fieldNames();it.hasNext();){String key=it.next();mapping.put(Integer.parseInt(key),s.get(key).asText());}
                yield ok(organizer.createPerformance(new CreatePerformanceRequest(a.id(),id(b,"eventID"),id(b,"venueID"),date(b,"date",false),LocalTime.parse(required(b,"startTime")),LocalTime.parse(required(b,"endTime")),tiers,mapping)));
            }
            case "organizer/price" -> changed(organizer.updateTierPrice(a.id(),id(b,"tierID"),money(b,"price",false)));
            case "organizer/block" -> changed(organizer.blockSeat(a.id(),id(b,"performanceID"),id(b,"seatID"),text(b,"reason")));
            case "organizer/unblock" -> changed(organizer.unblockSeat(a.id(),id(b,"performanceID"),id(b,"seatID")));
            case "organizer/cancel" -> changed(organizer.cancelPerformance(a.id(),id(b,"performanceID")));
            case "organizer/suggest" -> table("tier,capacityShare,suggestedPrice,sellThrough,confidence,deltaRevenue",organizer.suggestPricing(id(b,"venueID"),id(b,"genreID"),date(b,"date",false)));
            case "organizer/estimate" -> ok(organizer.estimateDeltaRevenue(number(b,"suggestedPrice"),number(b,"capacity"),number(b,"sellThrough"),number(b,"delta")));
            default -> throw new Failure(404,"Unknown workflow.");
        };
    }
    static Object changed(boolean value){if(!value)throw new Failure(409,"No change: check ownership, availability and the operation's time window.");return ok(true);}
    static JsonNode object(JsonNode b,String key)throws Exception{JsonNode n=b.path(key);if(n.isTextual())n=WebServer.JSON.readTree(n.asText());if(!n.isObject())throw new Failure(400,key+" must be an object.");return n;}
}
