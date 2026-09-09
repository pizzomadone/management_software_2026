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
            DatabaseMigrationManager::migrateReleaseIntegrity),
        new Migration("007", "Unify minimum stock and protect supplier document numbers",
            DatabaseMigrationManager::migrateStockThresholds),
        new Migration("008", "Store database currency and invoice snapshots",
            DatabaseMigrationManager::migrateInvoiceSnapshots),
        new Migration("009", "Validate numeric domains consistently in every module", DatabaseIntegrity::installGuards),
        new Migration("010", "Store minimum quantity only with warehouse reorder settings",
            DatabaseMigrationManager::normalizeStockThresholds)
    );

    private DatabaseMigrationManager() {}

    public static void migrate(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Migrations require an independent connection");
        try (Statement s = connection.createStatement()) { s.execute("PRAGMA foreign_keys=OFF"); }
        DatabaseManager.beginTransaction(connection);
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

    private static void migrateStockThresholds(Connection c) throws SQLException {
        try (Statement s=c.createStatement()) {
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_supplier_order_number ON supplier_orders(number)");
            // Newly created archives already omit the legacy product threshold.
            if (!hasColumn(c, "products", "minimum_quantity")) return;
            s.execute("UPDATE products SET minimum_quantity=(SELECT minimum_quantity FROM minimum_stock WHERE product_id=products.id) " +
                "WHERE id IN (SELECT product_id FROM minimum_stock)");
            s.execute("CREATE TRIGGER IF NOT EXISTS minimum_stock_insert AFTER INSERT ON minimum_stock BEGIN " +
                "UPDATE products SET minimum_quantity=NEW.minimum_quantity WHERE id=NEW.product_id; END");
            s.execute("CREATE TRIGGER IF NOT EXISTS minimum_stock_update AFTER UPDATE OF minimum_quantity ON minimum_stock BEGIN " +
                "UPDATE products SET minimum_quantity=NEW.minimum_quantity WHERE id=NEW.product_id; END");
            s.execute("CREATE TRIGGER IF NOT EXISTS product_minimum_update AFTER UPDATE OF minimum_quantity ON products " +
                "WHEN OLD.minimum_quantity<>NEW.minimum_quantity BEGIN " +
                "UPDATE minimum_stock SET minimum_quantity=NEW.minimum_quantity WHERE product_id=NEW.id; END");
        }
    }

    private static void normalizeStockThresholds(Connection c) throws SQLException {
        try (Statement s=c.createStatement()) {
            for (String trigger : List.of("minimum_stock_insert", "minimum_stock_update", "product_minimum_update"))
                s.execute("DROP TRIGGER IF EXISTS " + trigger);
        }
        // Replace old product guards before removing the column they referenced.
        DatabaseIntegrity.installGuards(c);
        if (!hasColumn(c, "products", "minimum_quantity")) return;
        try (Statement s=c.createStatement()) {
            // Existing warehouse settings, including an explicit zero, take precedence.
            // Only products without settings need their legacy threshold transferred.
            s.execute("""
                INSERT INTO minimum_stock(product_id, minimum_quantity, reorder_quantity)
                SELECT p.id, p.minimum_quantity, 0 FROM products p
                WHERE NOT EXISTS (SELECT 1 FROM minimum_stock sm WHERE sm.product_id=p.id)
                """);
            s.execute("ALTER TABLE products DROP COLUMN minimum_quantity");
        }
    }

    private static void migrateInvoiceSnapshots(Connection c) throws SQLException {
        DatabaseCurrency.initialize(c, DatabaseCurrency.codeFromSetting(SettingsPanel.getGlobalSetting("currency","EUR - Euro (€)")));
        for(String column:List.of("currency_code","customer_snapshot","company_snapshot"))
            addColumnIfMissing(c,"invoices",column,"ALTER TABLE invoices ADD COLUMN " + column + " TEXT");
        addColumnIfMissing(c,"invoices","logo_snapshot","ALTER TABLE invoices ADD COLUMN logo_snapshot BLOB");
        addColumnIfMissing(c,"invoices","snapshot_version","ALTER TABLE invoices ADD COLUMN snapshot_version INTEGER");
        for(String column:List.of("product_code","product_name"))
            addColumnIfMissing(c,"invoice_details",column,"ALTER TABLE invoice_details ADD COLUMN " + column + " TEXT");
        InvoiceSnapshots.captureLegacy(c);
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
