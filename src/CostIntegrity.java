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

    /** Only frozen sale costs or complete, priced FIFO consumption snapshots are evidence. */
    public static String historicalCostSql(String detailAlias) {
        if (!"d".equals(detailAlias)) throw new IllegalArgumentException("Unsupported detail alias");
        return """
            COALESCE(d.cost_total, (
                SELECT CASE WHEN SUM(x.quantity)=d.quantity
                    AND SUM(CASE WHEN x.lot_id IS NULL
                        OR (l.source_type='MIGRATION' AND x.unit_cost=0)
                        THEN 1 ELSE 0 END)=0
                    THEN SUM(x.quantity*x.unit_cost) ELSE NULL END
                FROM inventory_lot_consumptions x
                LEFT JOIN inventory_lots l ON l.id=x.lot_id
                WHERE x.order_detail_id=d.id
            ))
            """;
    }

    public static void requirePurchaseCost(Connection c, Collection<Integer> ids) throws SQLException {
        String message = missingPurchaseCostMessage(c, ids);
        if (message != null) throw new SQLException(message);
    }

    public static String summary(Audit audit) {
        String message = I18n.format(
            "Import completed: {0} product(s) without purchase cost, {1} sale(s) with historical cost that cannot be reconstructed.",
            audit.productsWithoutPurchaseCost(), audit.salesWithoutHistoricalCost());
        if (audit.productsWithoutPurchaseCost() > 0) message += "\n" + I18n.tr(
            "Products without purchase cost are highlighted in orange and cannot be purchased or sold until the cost is entered in the product sheet.");
        if (audit.salesWithoutHistoricalCost() > 0) message += "\n" + I18n.tr(
            "Sales without reconstructable historical cost remain visible but are excluded from historical cost, gross profit and other calculations that require that cost.");
        return message;
    }

    public static Audit audit(Connection c) throws SQLException {
        int products = 0;
        try (PreparedStatement p = c.prepareStatement(
                """
                SELECT COUNT(*)
                FROM products p
                LEFT JOIN product_suppliers ps
                  ON ps.product_id=p.id AND ps.is_preferred=1
                WHERE ps.purchase_price IS NULL OR ps.purchase_price<=0
                """);
             ResultSet r = p.executeQuery()) {
            if (r.next()) products = r.getInt(1);
        }

        int sales = 0;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(DISTINCT o.id) FROM orders o " +
                "JOIN order_details d ON d.order_id=o.id " +
                "WHERE o.status='Completed' AND (" + historicalCostSql("d") + ") IS NULL");
             ResultSet r = p.executeQuery()) {
            if (r.next()) sales = r.getInt(1);
        }
        return new Audit(products, sales);
    }

    public static boolean hasPurchaseCost(Connection c, int productId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                SELECT p.id, ps.purchase_price
                FROM products p
                LEFT JOIN product_suppliers ps
                  ON ps.product_id=p.id AND ps.is_preferred=1
                WHERE p.id=?
                """)) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Product no longer exists");
                Object value = r.getObject("purchase_price");
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

