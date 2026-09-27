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
            DatabaseMigrationManager::normalizeStockThresholds),
        new Migration("011", "Add product image metadata and constraints",
            DatabaseMigrationManager::migrateProductImages),
        new Migration("012", "Replace supplier price lists with product suppliers and add FIFO cost history",
            DatabaseMigrationManager::migrateProductSuppliersAndFifo)
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

    private static void migrateProductImages(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS product_images (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    filename TEXT NOT NULL,
                    is_primary INTEGER NOT NULL DEFAULT 0 CHECK(is_primary IN (0,1)),
                    sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order >= 0),
                    FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images(product_id)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_product_images_one_primary " +
                "ON product_images(product_id) WHERE is_primary=1");
            s.execute("""
                CREATE TRIGGER IF NOT EXISTS product_images_max_three
                BEFORE INSERT ON product_images
                WHEN (SELECT COUNT(*) FROM product_images WHERE product_id=NEW.product_id) >= 3
                BEGIN SELECT RAISE(ABORT,'Maximum three product images'); END
            """);
            s.execute("""
                CREATE TRIGGER IF NOT EXISTS product_images_max_three_update
                BEFORE UPDATE OF product_id ON product_images
                WHEN NEW.product_id<>OLD.product_id
                 AND (SELECT COUNT(*) FROM product_images WHERE product_id=NEW.product_id) >= 3
                BEGIN SELECT RAISE(ABORT,'Maximum three product images'); END
            """);
        }
    }


    private static void migrateProductSuppliersAndFifo(Connection c) throws SQLException {
        addColumnIfMissing(c, "order_details", "cost_total",
            "ALTER TABLE order_details ADD COLUMN cost_total REAL");

        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS product_suppliers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    supplier_id INTEGER NOT NULL,
                    supplier_product_code TEXT,
                    purchase_price REAL NOT NULL DEFAULT 0.0,
                    is_preferred INTEGER NOT NULL DEFAULT 0 CHECK(is_preferred IN (0,1)),
                    notes TEXT,
                    UNIQUE(product_id, supplier_id),
                    FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS inventory_lots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    supplier_id INTEGER,
                    supplier_order_detail_id INTEGER,
                    warehouse_movement_id INTEGER,
                    received_date DATETIME NOT NULL,
                    initial_quantity INTEGER NOT NULL CHECK(initial_quantity >= 0),
                    remaining_quantity INTEGER NOT NULL CHECK(remaining_quantity >= 0 AND remaining_quantity <= initial_quantity),
                    unit_cost REAL NOT NULL CHECK(unit_cost >= 0),
                    source_type TEXT NOT NULL,
                    FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY(supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY(supplier_order_detail_id) REFERENCES supplier_order_details(id),
                    FOREIGN KEY(warehouse_movement_id) REFERENCES warehouse_movements(id)
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS inventory_lot_consumptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lot_id INTEGER,
                    order_detail_id INTEGER,
                    warehouse_movement_id INTEGER,
                    quantity INTEGER NOT NULL CHECK(quantity > 0),
                    unit_cost REAL NOT NULL CHECK(unit_cost >= 0),
                    FOREIGN KEY(lot_id) REFERENCES inventory_lots(id),
                    FOREIGN KEY(order_detail_id) REFERENCES order_details(id) ON DELETE CASCADE,
                    FOREIGN KEY(warehouse_movement_id) REFERENCES warehouse_movements(id) ON DELETE CASCADE
                )
            """);

            // Existing primary supplier becomes the preferred supplier relation.
            if (hasColumn(c, "products", "supplier_id")) {
                s.execute("""
                    INSERT OR IGNORE INTO product_suppliers
                        (product_id, supplier_id, purchase_price, is_preferred)
                    SELECT id, supplier_id, acquisition_cost, 1
                    FROM products WHERE supplier_id IS NOT NULL
                """);
            }

            // Preserve every old supplier/product association. Multiple price-list
            // rows collapse to the most recently defined supplier price.
            if (hasTable(c, "supplier_price_lists")) {
                s.execute("""
                    INSERT OR IGNORE INTO product_suppliers
                        (product_id, supplier_id, supplier_product_code, purchase_price, is_preferred, notes)
                    SELECT spl.product_id, spl.supplier_id, spl.supplier_product_code, spl.price, 0, spl.notes
                    FROM supplier_price_lists spl
                    WHERE spl.id = (
                        SELECT x.id FROM supplier_price_lists x
                        WHERE x.product_id=spl.product_id AND x.supplier_id=spl.supplier_id
                        ORDER BY x.validity_start_date DESC, x.id DESC LIMIT 1
                    )
                """);
                s.execute("""
                    UPDATE product_suppliers
                    SET supplier_product_code = COALESCE((
                            SELECT x.supplier_product_code FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY x.validity_start_date DESC, x.id DESC LIMIT 1
                        ), supplier_product_code),
                        purchase_price = COALESCE((
                            SELECT x.price FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY x.validity_start_date DESC, x.id DESC LIMIT 1
                        ), purchase_price),
                        notes = COALESCE((
                            SELECT x.notes FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY x.validity_start_date DESC, x.id DESC LIMIT 1
                        ), notes)
                    WHERE EXISTS (
                        SELECT 1 FROM supplier_price_lists x
                        WHERE x.product_id=product_suppliers.product_id
                          AND x.supplier_id=product_suppliers.supplier_id
                    )
                """);
            }

            // Preserve a reorder supplier that was different from the legacy product supplier.
            if (hasColumn(c, "minimum_stock", "preferred_supplier_id")) {
                s.execute("""
                    INSERT OR IGNORE INTO product_suppliers
                        (product_id, supplier_id, purchase_price, is_preferred)
                    SELECT sm.product_id, sm.preferred_supplier_id, p.acquisition_cost,
                           CASE WHEN NOT EXISTS (
                               SELECT 1 FROM product_suppliers ps
                               WHERE ps.product_id=sm.product_id AND ps.is_preferred=1
                           ) THEN 1 ELSE 0 END
                    FROM minimum_stock sm
                    JOIN products p ON p.id=sm.product_id
                    WHERE sm.preferred_supplier_id IS NOT NULL
                """);
            }

            // Any product with suppliers must have exactly one preferred supplier.
            s.execute("""
                UPDATE product_suppliers SET is_preferred=1
                WHERE id IN (
                    SELECT MIN(ps.id) FROM product_suppliers ps
                    WHERE NOT EXISTS (
                        SELECT 1 FROM product_suppliers pref
                        WHERE pref.product_id=ps.product_id AND pref.is_preferred=1
                    )
                    GROUP BY ps.product_id
                )
            """);

            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_product_suppliers_pair ON product_suppliers(product_id,supplier_id)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_product_suppliers_preferred ON product_suppliers(product_id) WHERE is_preferred=1");
            s.execute("CREATE INDEX IF NOT EXISTS idx_product_suppliers_supplier ON product_suppliers(supplier_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_inventory_lots_fifo ON inventory_lots(product_id,received_date,id)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_lots_supplier_detail ON inventory_lots(supplier_order_detail_id) WHERE supplier_order_detail_id IS NOT NULL");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_lots_movement ON inventory_lots(warehouse_movement_id) WHERE warehouse_movement_id IS NOT NULL");
            s.execute("CREATE INDEX IF NOT EXISTS idx_lot_consumptions_order_detail ON inventory_lot_consumptions(order_detail_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_lot_consumptions_movement ON inventory_lot_consumptions(warehouse_movement_id)");

            // Legacy completed supplier orders are real purchase history, but the old
            // database cannot tell which of their units are still on hand. Preserve them as
            // exhausted historical lots. This also prevents a migrated completed receipt from
            // being reversed and corrupting today's stock.
            s.execute("""
                INSERT OR IGNORE INTO inventory_lots
                    (product_id,supplier_id,supplier_order_detail_id,received_date,
                     initial_quantity,remaining_quantity,unit_cost,source_type)
                SELECT d.product_id,o.supplier_id,d.id,o.order_date,
                       d.quantity,0,d.unit_price,'LEGACY_SUPPLIER_ORDER'
                FROM supplier_order_details d
                JOIN supplier_orders o ON o.id=d.order_id
                WHERE o.status='Completed'
            """);

            // Existing on-hand stock cannot be assigned reliably to those historical orders.
            // Start the current balance as one migration layer at the product's current
            // acquisition cost. Old customer sales intentionally keep cost_total=NULL.
            s.execute("""
                INSERT INTO inventory_lots
                    (product_id, received_date, initial_quantity, remaining_quantity, unit_cost, source_type)
                SELECT p.id, CURRENT_TIMESTAMP, p.quantity, p.quantity, p.acquisition_cost, 'MIGRATION'
                FROM products p
                WHERE p.quantity>0
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_lots l
                      WHERE l.product_id=p.id AND l.remaining_quantity>0
                  )
            """);

            // product_suppliers is the source of truth. Legacy columns remain as
            // compatibility caches for older code/database readers.
            s.execute("DROP TRIGGER IF EXISTS product_suppliers_preferred_insert");
            s.execute("DROP TRIGGER IF EXISTS product_suppliers_preferred_update");
            s.execute("DROP TRIGGER IF EXISTS product_suppliers_sync_insert");
            s.execute("DROP TRIGGER IF EXISTS product_suppliers_sync_update");
            s.execute("DROP TRIGGER IF EXISTS product_suppliers_sync_delete");

            s.execute("""
                CREATE TRIGGER product_suppliers_preferred_insert
                BEFORE INSERT ON product_suppliers
                WHEN NEW.is_preferred=1
                BEGIN
                    UPDATE product_suppliers SET is_preferred=0
                    WHERE product_id=NEW.product_id;
                END
            """);
            s.execute("""
                CREATE TRIGGER product_suppliers_preferred_update
                BEFORE UPDATE OF is_preferred ON product_suppliers
                WHEN NEW.is_preferred=1 AND OLD.is_preferred<>1
                BEGIN
                    UPDATE product_suppliers SET is_preferred=0
                    WHERE product_id=NEW.product_id AND id<>OLD.id;
                END
            """);
            s.execute("""
                CREATE TRIGGER product_suppliers_sync_insert
                AFTER INSERT ON product_suppliers
                BEGIN
                    UPDATE products SET supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=NEW.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE id=NEW.product_id;
                    UPDATE minimum_stock SET preferred_supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=NEW.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE product_id=NEW.product_id;
                END
            """);
            s.execute("""
                CREATE TRIGGER product_suppliers_sync_update
                AFTER UPDATE ON product_suppliers
                BEGIN
                    UPDATE products SET supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=NEW.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE id=NEW.product_id;
                    UPDATE minimum_stock SET preferred_supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=NEW.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE product_id=NEW.product_id;
                END
            """);
            s.execute("""
                CREATE TRIGGER product_suppliers_sync_delete
                AFTER DELETE ON product_suppliers
                BEGIN
                    UPDATE products SET supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=OLD.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE id=OLD.product_id;
                    UPDATE minimum_stock SET preferred_supplier_id=(
                        SELECT supplier_id FROM product_suppliers
                        WHERE product_id=OLD.product_id AND is_preferred=1 LIMIT 1
                    ) WHERE product_id=OLD.product_id;
                END
            """);

            if (hasColumn(c, "products", "supplier_id")) {
                s.execute("""
                    UPDATE products SET supplier_id=(
                        SELECT supplier_id FROM product_suppliers ps
                        WHERE ps.product_id=products.id AND ps.is_preferred=1 LIMIT 1
                    )
                """);
            }
            if (hasColumn(c, "minimum_stock", "preferred_supplier_id")) {
                s.execute("""
                    UPDATE minimum_stock SET preferred_supplier_id=(
                        SELECT supplier_id FROM product_suppliers ps
                        WHERE ps.product_id=minimum_stock.product_id AND ps.is_preferred=1 LIMIT 1
                    )
                """);
            }

            if (hasTable(c, "supplier_price_lists")) {
                s.execute("DROP TABLE supplier_price_lists");
            }
        }
        DatabaseIntegrity.installGuards(c);
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

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            p.setString(1, table);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
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
