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
            DatabaseMigrationManager::migrateInvoiceOrderRelation)
    );

    private DatabaseMigrationManager() {}

    public static void migrate(Connection connection) throws SQLException {
        createMigrationHistory(connection);
        for (Migration migration : MIGRATIONS) {
            if (!isApplied(connection, migration.version())) {
                applyMigration(connection, migration);
            }
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
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            migration.action().apply(connection);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, description) VALUES (?, ?)")) {
                pstmt.setString(1, migration.version());
                pstmt.setString(2, migration.description());
                pstmt.executeUpdate();
            }
            connection.commit();
            System.out.println("Applied database migration " + migration.version() +
                ": " + migration.description());
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Database migration " + migration.version() + " failed", e);
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void migrateSupplierData(Connection connection) throws SQLException {
        boolean hasLegacySupplier = hasColumn(connection, "products", "supplier");
        boolean hasSupplierId = hasColumn(connection, "products", "supplier_id");
        if (!hasLegacySupplier || hasSupplierId) return;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE products_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT UNIQUE NOT NULL, name TEXT NOT NULL, description TEXT,
                    price REAL NOT NULL, quantity INTEGER DEFAULT 0, category TEXT DEFAULT '',
                    alternative_sku TEXT DEFAULT '', weight REAL DEFAULT 0.0,
                    unit_of_measure TEXT DEFAULT 'pcs', minimum_quantity INTEGER DEFAULT 0,
                    acquisition_cost REAL DEFAULT 0.0, active INTEGER DEFAULT 1,
                    supplier_id INTEGER, FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
                )
            """);
            stmt.execute("""
                INSERT INTO products_new
                    (id, code, name, description, price, quantity, category, alternative_sku,
                     weight, unit_of_measure, minimum_quantity, acquisition_cost, active, supplier_id)
                SELECT p.id, p.code, p.name, p.description, p.price, p.quantity, p.category,
                       p.alternative_sku, p.weight, p.unit_of_measure, p.minimum_quantity,
                       p.acquisition_cost, p.active, s.id
                FROM products p LEFT JOIN suppliers s ON p.supplier = s.company_name
            """);
            stmt.execute("DROP TABLE products");
            stmt.execute("ALTER TABLE products_new RENAME TO products");
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
            "ALTER TABLE invoices ADD COLUMN order_id INTEGER");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_order_id " +
                "ON invoices(order_id) WHERE order_id IS NOT NULL");
        }
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
