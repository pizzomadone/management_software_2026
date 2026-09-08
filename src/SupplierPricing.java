import java.sql.*;
import java.util.Date;

public final class SupplierPricing {
    private SupplierPricing() {}
    public static double price(Connection c, int supplier, int product, int quantity, Date date) throws SQLException {
        Numbers.positiveQuantity(quantity);
        if (date == null) throw new IllegalArgumentException("Order date is required");
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
        String query = "SELECT price FROM supplier_price_lists WHERE supplier_id=? AND product_id=? " +
            "AND minimum_quantity<=? AND " + DateUtils.sqlDate("validity_start_date") + "<=? " +
            "AND (validity_end_date IS NULL OR " + DateUtils.sqlDate("validity_end_date") + ">=?) " +
            "ORDER BY minimum_quantity DESC, " + DateUtils.sqlDate("validity_start_date") + " DESC, id DESC LIMIT 1";
        try (PreparedStatement p = c.prepareStatement(query)) {
            p.setInt(1,supplier); p.setInt(2,product); p.setInt(3,quantity); p.setString(4,day); p.setString(5,day);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return Numbers.nonNegative(r.getDouble(1)); }
        }
        try (PreparedStatement p = c.prepareStatement("SELECT acquisition_cost FROM products WHERE id=?")) {
            p.setInt(1,product);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Product no longer exists");
                return Numbers.nonNegative(r.getDouble(1));
            }
        }
    }
}
