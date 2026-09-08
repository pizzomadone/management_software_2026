import java.sql.*;
import java.util.List;

/** Applies ordered database migrations independently from normal database access. */
public final class DatabaseMigrationManager {
    private record Migration(String version, String description, MigrationAction action) {}

    @FunctionalInterface
    private interface MigrationAction {
        void apply(Connection connection) throws SQLException;
    }

    private static final List<Migration> MIGRATIONS = List.of(
        new Migration("001", "Replace legacy product supplier text with supplier_id",
            DatabaseMigrationManager::migrateSupplierData),
        new Migration("002", "Add product stock reservation quantity",
            connection -> addColumnIfMissing(connection, "products", "reserved_quantity",
                "ALTER TABLE products ADD COLUMN reserved_quantity INTEGER DEFAULT 0")),
        new Migration("003", "Add product warehouse position and VAT rate",
            DatabaseMigrationManager::migrateWarehousePositionAndVat),
        new Migration("004", "Add order payment tracking",
            DatabaseMigrationManager::migratePaymentTracking),
        new Migration("005", "Link generated invoices to source orders",
            DatabaseMigrationManager::migrateInvoiceOrderRelation),
        new Migration("006", "Repair invoice relationships and guard persisted numeric ranges",
            DatabaseMigrationManager::migrateReleaseIntegrity)
    );

    private DatabaseMigrationManager() {}

    public static void migrate(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Migrations require an independent connection");
        try (Statement s = connection.createStatement()) { s.execute("PRAGMA foreign_keys=OFF"); }
        connection.setAutoCommit(false);
        try {
            createMigrationHistory(connection);
            for (Migration migration : MIGRATIONS) {
                if (!isApplied(connection, migration.version())) applyMigration(connection, migration);
            }
            try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
                if (r.next()) throw new SQLException("Database contains orphaned references in " + r.getString(1));
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
            try (Statement s = connection.createStatement()) { s.execute("PRAGMA foreign_keys=ON"); }
        }
    }

    private static void createMigrationHistory(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version TEXT PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    private static boolean isApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            pstmt.setString(1, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void applyMigration(Connection connection, Migration migration) throws SQLException {
        migration.action().apply(connection);
        try (PreparedStatement p = connection.prepareStatement(
                "INSERT INTO schema_migrations(version, description) VALUES (?,?)")) {
            p.setString(1, migration.version()); p.setString(2, migration.description()); p.executeUpdate();
        }
    }

    private static void migrateSupplierData(Connection connection) throws SQLException {
        boolean hasLegacySupplier = hasColumn(connection, "products", "supplier");
        boolean hasSupplierId = hasColumn(connection, "products", "supplier_id");
        if (!hasLegacySupplier || hasSupplierId) return;

        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery("SELECT p.code FROM products p WHERE " +
                    "COALESCE(TRIM(p.supplier),'')<>'' AND " +
                    "(SELECT COUNT(*) FROM suppliers s WHERE s.company_name=p.supplier)<>1 LIMIT 1")) {
                if (r.next()) throw new SQLException("Legacy supplier must match exactly one supplier for product " + r.getString(1));
            }
            stmt.execute("ALTER TABLE products ADD COLUMN supplier_id INTEGER REFERENCES suppliers(id)");
            stmt.execute("UPDATE products SET supplier_id=(SELECT s.id FROM suppliers s WHERE s.company_name=products.supplier)");
        }
    }

    private static void migrateWarehousePositionAndVat(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "products", "warehouse_position",
            "ALTER TABLE products ADD COLUMN warehouse_position TEXT DEFAULT ''");
        addColumnIfMissing(connection, "products", "vat_rate",
            "ALTER TABLE products ADD COLUMN vat_rate REAL DEFAULT 0.0");
    }

    private static void migratePaymentTracking(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "orders", "payment_status",
            "ALTER TABLE orders ADD COLUMN payment_status TEXT DEFAULT 'NOT_PAID'");
        addColumnIfMissing(connection, "orders", "paid_amount",
            "ALTER TABLE orders ADD COLUMN paid_amount REAL DEFAULT 0.0");
    }

    private static void migrateInvoiceOrderRelation(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "invoices", "order_id",
            "ALTER TABLE invoices ADD COLUMN order_id INTEGER REFERENCES orders(id)");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_order_id " +
                "ON invoices(order_id) WHERE order_id IS NOT NULL");
        }
    }

    private static void migrateReleaseIntegrity(Connection c) throws SQLException {
        boolean linked = false;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_key_list(invoices)")) {
            while (r.next()) if ("order_id".equals(r.getString("from"))) linked = true;
        }
        try (Statement s = c.createStatement()) {
            if (!linked) {
                try (ResultSet r = s.executeQuery("SELECT id FROM invoices WHERE order_id IS NOT NULL AND order_id NOT IN (SELECT id FROM orders) LIMIT 1")) {
                    if (r.next()) throw new SQLException("Invoice " + r.getInt(1) + " references a missing order; reconcile it before migration");
                }
                s.execute("CREATE TABLE invoices_checked (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT UNIQUE NOT NULL, " +
                    "date DATETIME NOT NULL, customer_id INTEGER REFERENCES customers(id), taxable_amount REAL NOT NULL, " +
                    "vat REAL NOT NULL, total REAL NOT NULL, status TEXT NOT NULL, order_id INTEGER REFERENCES orders(id))");
                s.execute("INSERT INTO invoices_checked SELECT id,number,date,customer_id,taxable_amount,vat,total,status,order_id FROM invoices");
                s.execute("DROP TABLE invoices");
                s.execute("ALTER TABLE invoices_checked RENAME TO invoices");
            }
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_order_id ON invoices(order_id) WHERE order_id IS NOT NULL");
        }
        DatabaseIntegrity.installGuards(c);
    }

    private static void addColumnIfMissing(Connection connection, String table, String column,
                                           String alterStatement) throws SQLException {
        if (!hasColumn(connection, table, column)) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(alterStatement);
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) return true;
            }
            return false;
        }
    }
}
