package mytix;

import mytix.database.catalog.CatalogRepository;
import mytix.database.catalog.JdbcCatalogRepository;
import mytix.database.organizer.JdbcOrganizerService;
import mytix.database.organizer.OrganizerService;
import mytix.database.query.JdbcQueryService;
import mytix.database.query.QueryService;
import mytix.database.report.JdbcReportService;
import mytix.database.report.ReportService;
import mytix.database.transaction.BookingService;
import mytix.database.transaction.JdbcBookingService;
import mytix.database.user.JdbcUserRepository;
import mytix.database.user.UserRepository;
import mytix.session.SessionContext;

public final class AppContext {

    private final SessionContext session = new SessionContext();
    private final UserRepository users = new JdbcUserRepository();
    private final CatalogRepository catalog = new JdbcCatalogRepository();
    private final BookingService booking = new JdbcBookingService();
    private final OrganizerService organizer = new JdbcOrganizerService();
    private final QueryService queries = new JdbcQueryService();
    private final ReportService reports = new JdbcReportService();

    public SessionContext session() {
        return session;
    }

    public UserRepository users() {
        return users;
    }

    public CatalogRepository catalog() {
        return catalog;
    }

    public BookingService booking() {
        return booking;
    }

    public OrganizerService organizer() {
        return organizer;
    }

    public QueryService queries() {
        return queries;
    }

    public ReportService reports() {
        return reports;
    }
}
