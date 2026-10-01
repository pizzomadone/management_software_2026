import java.sql.*;

/** Reads the current purchase price for one product/supplier relationship. */
public final class SupplierPricing {
    private SupplierPricing() {}

    public record PreferredSupplier(int relationId, String name, double price) {}

    /** Called after removal of the preferred relationship, inside the caller's transaction. */
    public static PreferredSupplier preferCheapestRemaining(Connection c, int productId) throws SQLException {
        PreferredSupplier selected=null;
        try (PreparedStatement p=c.prepareStatement("""
                SELECT ps.id,s.company_name,ps.purchase_price FROM product_suppliers ps
                JOIN suppliers s ON s.id=ps.supplier_id WHERE ps.product_id=?
                ORDER BY ps.purchase_price,ps.id LIMIT 1
                """)) {
            p.setInt(1,productId);
            try (ResultSet r=p.executeQuery()) {
                if (r.next()) selected=new PreferredSupplier(r.getInt(1),r.getString(2),r.getDouble(3));
            }
        }
        if (selected!=null) {
            try (PreparedStatement p=c.prepareStatement("UPDATE product_suppliers SET is_preferred=1 WHERE id=?")) {
                p.setInt(1,selected.relationId());p.executeUpdate();
            }
        }
        return selected;
    }

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

