import java.sql.*;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shared checks for purchase-cost completeness and historical FIFO cost coverage. */
public final class CostIntegrity {
    public record Audit(int productsWithoutPurchaseCost, int salesWithoutHistoricalCost) {
        public boolean hasIssues() {
            return productsWithoutPurchaseCost > 0 || salesWithoutHistoricalCost > 0;
        }
    }

    private CostIntegrity() {}

    public static Audit audit(Connection c) throws SQLException {
        int products = 0;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM products WHERE active=1 AND acquisition_cost<=0");
             ResultSet r = p.executeQuery()) {
            if (r.next()) products = r.getInt(1);
        }

        int sales = 0;
        try (PreparedStatement p = c.prepareStatement("""
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                JOIN order_details d ON d.order_id=o.id
                WHERE o.status='Completed' AND d.cost_total IS NULL
                """);
             ResultSet r = p.executeQuery()) {
            if (r.next()) sales = r.getInt(1);
        }
        return new Audit(products, sales);
    }

    public static boolean hasPurchaseCost(Connection c, int productId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT acquisition_cost FROM products WHERE id=?")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Product no longer exists");
                Object value = r.getObject(1);
                return value != null && Numbers.nonNegative(value) > 0.0;
            }
        }
    }

    public static String productDisplayName(Connection c, int productId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT code,name FROM products WHERE id=?")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Product no longer exists");
                String code = r.getString(1);
                String name = r.getString(2);
                if (code == null || code.isBlank()) return name == null ? "" : name;
                if (name == null || name.isBlank()) return code;
                return code + " - " + name;
            }
        }
    }

    public static Set<Integer> productsWithoutPurchaseCost(Connection c,
                                                            Collection<Integer> productIds)
            throws SQLException {
        Set<Integer> missing = new LinkedHashSet<>();
        if (productIds == null) return missing;
        for (Integer productId : new LinkedHashSet<>(productIds)) {
            if (productId != null && !hasPurchaseCost(c, productId)) missing.add(productId);
        }
        return missing;
    }

    public static String missingPurchaseCostMessage(Connection c, Collection<Integer> productIds)
            throws SQLException {
        Set<Integer> missing = productsWithoutPurchaseCost(c, productIds);
        if (missing.isEmpty()) return null;

        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (int productId : missing) {
            if (shown == 5) break;
            if (shown > 0) names.append("\n");
            names.append("- ").append(productDisplayName(c, productId));
            shown++;
        }
        if (missing.size() > shown) {
            names.append("\n").append(I18n.format("... and {0} more product(s).",
                missing.size() - shown));
        }

        return I18n.tr("Operation not available: one or more products have no purchase cost.")
            + "\n" + I18n.tr("Enter the purchase cost in the product sheet before making purchases or sales.")
            + "\n\n" + names;
    }

    public static void updateUnpricedOpeningLots(Connection c, int productId,
                                                  double previousCost, double newCost)
            throws SQLException {
        if (previousCost > 0 || newCost <= 0) return;
        try (PreparedStatement p = c.prepareStatement("""
                UPDATE inventory_lots
                SET unit_cost=?
                WHERE product_id=?
                  AND unit_cost=0
                  AND source_type='MIGRATION'
                  AND remaining_quantity>0
                """)) {
            p.setDouble(1, newCost);
            p.setInt(2, productId);
            p.executeUpdate();
        }
    }
}
