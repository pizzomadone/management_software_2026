import java.sql.*;
import java.util.*;

public final class DocumentNumbers {
    private DocumentNumbers() {}
    public static int nextSupplierSequence(Connection c, String prefix) throws SQLException {
        long maximum = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT number FROM supplier_orders WHERE number LIKE ?")) {
            p.setString(1, prefix + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String suffix = r.getString(1).substring(prefix.length());
                    if (!suffix.matches("[0-9]+")) continue;
                    try { maximum = Math.max(maximum, Long.parseLong(suffix)); }
                    catch (NumberFormatException e) { throw new SQLException("Supplier numbering exceeds the supported range",e); }
                }
            }
        }
        if (maximum >= Integer.MAX_VALUE) throw new SQLException("Supplier numbering exceeds the supported range");
        return (int)maximum + 1;
    }
    public static String nextSupplierNumber(Connection c, java.util.Date date) throws SQLException {
        Calendar calendar = Calendar.getInstance(); calendar.setTime(date);
        String prefix = "OF" + calendar.get(Calendar.YEAR);
        return String.format(Locale.ROOT,"%s%04d", prefix, nextSupplierSequence(c,prefix));
    }
}
