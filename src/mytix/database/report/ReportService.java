package mytix.database.report;

import java.util.List;

public interface ReportService {

    
    List<List<String>> runReport(int reportNumber, List<String> params);

    
    List<String> headersFor(int reportNumber, List<String> params);
}
