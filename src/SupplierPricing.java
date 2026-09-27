import java.sql.*;

/** Reads the current purchase price for one product/supplier relationship. */
public final class SupplierPricing {
    private SupplierPricing() {}

    public static double price(Connection c, int supplier, int product)
            throws SQLException {
        try (PreparedStatement p=c.prepareStatement(
                "SELECT purchase_price FROM product_suppliers WHERE supplier_id=? AND product_id=?")) {
            p.setInt(1,supplier);p.setInt(2,product);
            try (ResultSet r=p.executeQuery()) {
                if(r.next()) return Numbers.nonNegative(r.getObject(1));
            }
        }
        throw new SQLException("This product is not supplied by the selected supplier");
    }
}
