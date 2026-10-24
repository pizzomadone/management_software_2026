import java.sql.*;

/** Explains empty sales without guessing or synthesizing transactions. */
public final class SalesDataDiagnostics {
    private SalesDataDiagnostics() {}

    public static String explainEmptySales(Connection conn) throws SQLException {
        try (Statement s=conn.createStatement();ResultSet r=s.executeQuery("""
            SELECT COUNT(*) AS completed,
                   COUNT(warestat_date(COALESCE(completed_date,order_date))) AS dated,
                   MIN(warestat_date(COALESCE(completed_date,order_date))) AS earliest,
                   MAX(warestat_date(COALESCE(completed_date,order_date))) AS latest
            FROM orders WHERE status='Completed'
            """)) {
            if(!r.next())return "";
            long completed=r.getLong("completed");
            if(completed==0)
                return I18n.tr("No completed customer orders exist. These reports use completed orders, not invoices alone.");
            long dated=r.getLong("dated");
            if(dated==0)
                return I18n.tr("Completed customer orders exist, but their dates are not recognized.");
            return I18n.tr("No completed customer orders in the selected date range.")+
                " "+I18n.tr("Available sales dates:")+" "+
                r.getString("earliest")+" — "+r.getString("latest")+".";
        }
    }
}
