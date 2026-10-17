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
            DatabaseMigrationManager::migrateProductSuppliersAndFifo),
        new Migration("013", "Mark unreconstructable zero-cost migration sales as unknown",
            DatabaseMigrationManager::markUnknownHistoricalCosts),
        new Migration("014", "Preserve manual FIFO reversals and unknown cost snapshots",
            DatabaseMigrationManager::migrateFifoReversals),
        new Migration("015", "Store actual supplier receipt date",
            DatabaseMigrationManager::migrateSupplierReceiptDate),
        new Migration("016", "Separate invoice document status from payment status",
            DatabaseMigrationManager::migrateInvoicePaymentStatus),
        new Migration("017", "Use explicit VAT-inclusive and VAT-exclusive sale price fields",
            DatabaseMigrationManager::migrateSalePriceSemantics),
        new Migration("018", "Make new customer orders non-stock reservations",
            DatabaseMigrationManager::releaseNewOrderReservations),
        new Migration("019", "Reclassify legacy new orders and reserve fully available stock",
            DatabaseMigrationManager::migrateCustomerOrderReservationStates),
        new Migration("020", "Store actual customer order completion date",
            DatabaseMigrationManager::migrateCustomerOrderCompletionDate),
        new Migration("021", "Record partial and complete supplier returns",
            SupplierReturnManager::installSchema),
        new Migration("022", "Embed optimized product JPEGs directly in SQLite",
            c -> addColumnIfMissing(c, "product_images", "image_data",
                "ALTER TABLE product_images ADD COLUMN image_data BLOB"))
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


    private static void migrateFifoReversals(Connection c) throws SQLException {
        addColumnIfMissing(c, "warehouse_movements", "reversal_of_movement_id",
            "ALTER TABLE warehouse_movements ADD COLUMN reversal_of_movement_id INTEGER REFERENCES warehouse_movements(id)");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_movement_reversal ON warehouse_movements(reversal_of_movement_id) WHERE reversal_of_movement_id IS NOT NULL");
            // A later correction of an opening layer must not price a previous sale.
            s.executeUpdate("""
                UPDATE order_details SET cost_total=NULL WHERE id IN (
                    SELECT x.order_detail_id FROM inventory_lot_consumptions x
                    JOIN inventory_lots l ON l.id=x.lot_id
                    WHERE l.source_type='MIGRATION' AND x.unit_cost=0
                )
                """);
        }
    }

    private static void migrateSupplierReceiptDate(Connection c) throws SQLException {
        addColumnIfMissing(c, "supplier_orders", "received_date",
            "ALTER TABLE supplier_orders ADD COLUMN received_date DATETIME");
        try (Statement s = c.createStatement()) {
            // Receipts posted by this FIFO version already have an authoritative lot date.
            // Preserve it so upgrading cannot reorder existing active FIFO layers.
            s.executeUpdate("""
                UPDATE supplier_orders
                SET received_date = COALESCE(
                    (
                        SELECT MIN(l.received_date)
                        FROM supplier_order_details d
                        JOIN inventory_lots l ON l.supplier_order_detail_id=d.id
                        WHERE d.order_id=supplier_orders.id
                          AND l.source_type='SUPPLIER_ORDER'
                    ),
                    expected_delivery_date,
                    order_date
                )
                WHERE status='Completed' AND received_date IS NULL
                """);
            // Non-completed orders have not been received yet.
            s.executeUpdate("""
                UPDATE supplier_orders
                SET received_date=NULL
                WHERE status<>'Completed'
                """);
        }
    }

    private static void migrateInvoicePaymentStatus(Connection c) throws SQLException {
        addColumnIfMissing(c, "invoices", "payment_status",
            "ALTER TABLE invoices ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'NOT_PAID'");
        addColumnIfMissing(c, "invoices", "paid_amount",
            "ALTER TABLE invoices ADD COLUMN paid_amount REAL NOT NULL DEFAULT 0.0");

        try (Statement s = c.createStatement()) {
            // In legacy databases, Paid was incorrectly used as a document status.
            // Preserve the recorded payment only for standalone invoices.
            s.executeUpdate("""
                UPDATE invoices
                SET payment_status='PAID', paid_amount=total
                WHERE status='Paid' AND order_id IS NULL
                """);

            // Linked invoices never keep a second payment source.
            s.executeUpdate("""
                UPDATE invoices
                SET payment_status='NOT_PAID', paid_amount=0
                WHERE order_id IS NOT NULL
                """);

            s.executeUpdate("""
                UPDATE invoices
                SET status='Issued'
                WHERE status='Paid'
                """);
        }
        DatabaseIntegrity.installGuards(c);
    }

    private static void migrateSalePriceSemantics(Connection c) throws SQLException {
        // Rename ambiguous legacy sale columns without changing their numeric meaning.
        // Legacy product.price and sales unit_price values were VAT-exclusive.
        try (Statement s = c.createStatement()) {
            if (hasColumn(c, "products", "price") &&
                    !hasColumn(c, "products", "sale_price_ex_vat")) {
                s.execute("ALTER TABLE products RENAME COLUMN price TO sale_price_ex_vat");
            }
            if (hasColumn(c, "orders", "total") &&
                    !hasColumn(c, "orders", "total_ex_vat")) {
                s.execute("ALTER TABLE orders RENAME COLUMN total TO total_ex_vat");
            }
            if (hasColumn(c, "order_details", "unit_price") &&
                    !hasColumn(c, "order_details", "sale_unit_price_ex_vat")) {
                s.execute("ALTER TABLE order_details RENAME COLUMN unit_price TO sale_unit_price_ex_vat");
            }
            if (hasColumn(c, "invoice_details", "unit_price") &&
                    !hasColumn(c, "invoice_details", "sale_unit_price_ex_vat")) {
                s.execute("ALTER TABLE invoice_details RENAME COLUMN unit_price TO sale_unit_price_ex_vat");
            }
        }

        addColumnIfMissing(c, "products", "sale_price_inc_vat",
            "ALTER TABLE products ADD COLUMN sale_price_inc_vat REAL NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "orders", "total_inc_vat",
            "ALTER TABLE orders ADD COLUMN total_inc_vat REAL NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "order_details", "sale_unit_price_inc_vat",
            "ALTER TABLE order_details ADD COLUMN sale_unit_price_inc_vat REAL NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "order_details", "vat_rate",
            "ALTER TABLE order_details ADD COLUMN vat_rate REAL NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "invoice_details", "sale_unit_price_inc_vat",
            "ALTER TABLE invoice_details ADD COLUMN sale_unit_price_inc_vat REAL NOT NULL DEFAULT 0");

        // Existing products: preserve the old VAT-exclusive price exactly and derive
        // only the new customer-facing VAT-inclusive price.
        try (PreparedStatement read = c.prepareStatement(
                "SELECT id,sale_price_ex_vat,vat_rate FROM products");
             PreparedStatement write = c.prepareStatement(
                "UPDATE products SET sale_price_inc_vat=? WHERE id=?");
             ResultSet r = read.executeQuery()) {
            while (r.next()) {
                write.setDouble(1, SalePrice.incVatFromExVat(
                    r.getDouble("sale_price_ex_vat"), r.getDouble("vat_rate")));
                write.setInt(2, r.getInt("id"));
                write.addBatch();
            }
            write.executeBatch();
        }

        // Invoice lines already snapshot historical VAT and net line totals.
        // Reconstruct a gross unit value from the historical line total so quantity
        // and sub-cent unit prices still reconcile with the unchanged invoice header.
        try (PreparedStatement read = c.prepareStatement(
                "SELECT id,quantity,sale_unit_price_ex_vat,vat_rate,total FROM invoice_details");
             PreparedStatement write = c.prepareStatement(
                "UPDATE invoice_details SET sale_unit_price_inc_vat=? WHERE id=?");
             ResultSet r = read.executeQuery()) {
            while (r.next()) {
                int quantity = Numbers.positiveQuantity(r.getObject("quantity"));
                double vatRate = Numbers.vat(r.getObject("vat_rate"));
                double netLine = Numbers.nonNegative(r.getObject("total"));
                double grossLine = Numbers.add(netLine, Numbers.tax(netLine, vatRate));
                double grossUnit = grossLine / quantity;
                write.setDouble(1, Numbers.nonNegative(grossUnit));
                write.setInt(2, r.getInt("id"));
                write.addBatch();
            }
            write.executeBatch();
        }

        // Preserve every legacy order net unit price exactly. If the order has a linked
        // invoice, its historical VAT (and gross unit price when consistent) is preferred.
        // Only orders without an invoice fall back to the product's current VAT rate.
        try (PreparedStatement read = c.prepareStatement("""
                SELECT d.id,d.quantity,d.sale_unit_price_ex_vat,
                       p.vat_rate AS product_vat_rate,
                       x.vat_rate AS invoice_vat_rate,
                       x.sale_unit_price_ex_vat AS invoice_net,
                       x.sale_unit_price_inc_vat AS invoice_gross
                FROM order_details d
                LEFT JOIN products p ON p.id=d.product_id
                LEFT JOIN invoices i ON i.order_id=d.order_id
                LEFT JOIN invoice_details x ON x.id=COALESCE(
                    (SELECT MIN(x2.id) FROM invoice_details x2
                     WHERE x2.invoice_id=i.id AND x2.product_id=d.product_id
                       AND x2.quantity=d.quantity
                       AND ABS(x2.sale_unit_price_ex_vat-d.sale_unit_price_ex_vat)<=0.004),
                    (SELECT MIN(x2.id) FROM invoice_details x2
                     WHERE x2.invoice_id=i.id AND x2.product_id=d.product_id
                       AND ABS(x2.sale_unit_price_ex_vat-d.sale_unit_price_ex_vat)<=0.004),
                    (SELECT MIN(x2.id) FROM invoice_details x2
                     WHERE x2.invoice_id=i.id AND x2.product_id=d.product_id)
                )
                """);
             PreparedStatement write = c.prepareStatement("""
                UPDATE order_details
                SET vat_rate=?,sale_unit_price_inc_vat=?
                WHERE id=?
                """);
             ResultSet r = read.executeQuery()) {
            while (r.next()) {
                double net = r.getDouble("sale_unit_price_ex_vat");
                Object invoiceVat = r.getObject("invoice_vat_rate");
                double vatRate = invoiceVat != null
                    ? Numbers.vat(invoiceVat)
                    : Numbers.vat(r.getObject("product_vat_rate"));

                int quantity = Numbers.positiveQuantity(r.getObject("quantity"));
                double netLine = Numbers.lineTotal(quantity, net);
                double gross = Numbers.add(netLine, Numbers.tax(netLine, vatRate)) / quantity;
                Object invoiceNet = r.getObject("invoice_net");
                Object invoiceGross = r.getObject("invoice_gross");
                if (invoiceVat != null && invoiceNet != null && invoiceGross != null &&
                        Math.abs(Numbers.nonNegative(invoiceNet) - net) <= 0.004) {
                    gross = Numbers.nonNegative(invoiceGross);
                }

                write.setDouble(1, vatRate);
                write.setDouble(2, gross);
                write.setInt(3, r.getInt("id"));
                write.addBatch();
            }
            write.executeBatch();
        }

        // Preserve the historical net order total. The new gross total comes from the
        // linked invoice when present, otherwise from the migrated order snapshots.
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                UPDATE orders
                SET total_inc_vat=COALESCE(
                    (SELECT i.total FROM invoices i
                     WHERE i.order_id=orders.id ORDER BY i.id LIMIT 1),
                    (SELECT SUM(warestat_line_total(
                         d.quantity,d.sale_unit_price_inc_vat))
                     FROM order_details d WHERE d.order_id=orders.id),
                    total_ex_vat
                )
                """);
        }

        // Never invent received money. paid_amount is preserved exactly; only the status
        // is reconciled against the customer-facing amount due.
        try (PreparedStatement read = c.prepareStatement("""
                SELECT o.id,o.paid_amount,
                       COALESCE(i.total,o.total_inc_vat) AS amount_due
                FROM orders o
                LEFT JOIN invoices i ON i.order_id=o.id
                """);
             PreparedStatement write = c.prepareStatement(
                "UPDATE orders SET payment_status=? WHERE id=?");
             ResultSet r = read.executeQuery()) {
            while (r.next()) {
                double paid = Numbers.nonNegative(r.getObject("paid_amount"));
                double due = Numbers.nonNegative(r.getObject("amount_due"));
                String status = PaymentAmounts.status(paid, due);
                write.setString(1, status);
                write.setInt(2, r.getInt("id"));
                write.addBatch();
            }
            write.executeBatch();
        }

        DatabaseIntegrity.installGuards(c);
    }

    private static void markUnknownHistoricalCosts(Connection c) throws SQLException {
        if (!hasTable(c, "inventory_lots") || !hasTable(c, "inventory_lot_consumptions")
                || !hasColumn(c, "order_details", "cost_total")) return;
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                UPDATE order_details
                SET cost_total=NULL
                WHERE id IN (
                    SELECT DISTINCT x.order_detail_id
                    FROM inventory_lot_consumptions x
                    JOIN inventory_lots l ON l.id=x.lot_id
                    WHERE x.order_detail_id IS NOT NULL
                      AND l.source_type='MIGRATION'
                      AND l.unit_cost=0
                )
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
                        ORDER BY CASE
                            WHEN warestat_date(x.validity_start_date) <= date('now')
                             AND (x.validity_end_date IS NULL OR warestat_date(x.validity_end_date) >= date('now'))
                            THEN 0 ELSE 1 END,
                            x.validity_start_date DESC, x.id DESC LIMIT 1
                    )
                """);
                s.execute("""
                    UPDATE product_suppliers
                    SET supplier_product_code = COALESCE((
                            SELECT x.supplier_product_code FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY CASE
                            WHEN warestat_date(x.validity_start_date) <= date('now')
                             AND (x.validity_end_date IS NULL OR warestat_date(x.validity_end_date) >= date('now'))
                            THEN 0 ELSE 1 END,
                            x.validity_start_date DESC, x.id DESC LIMIT 1
                        ), supplier_product_code),
                        purchase_price = COALESCE((
                            SELECT x.price FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY CASE
                            WHEN warestat_date(x.validity_start_date) <= date('now')
                             AND (x.validity_end_date IS NULL OR warestat_date(x.validity_end_date) >= date('now'))
                            THEN 0 ELSE 1 END,
                            x.validity_start_date DESC, x.id DESC LIMIT 1
                        ), purchase_price),
                        notes = COALESCE((
                            SELECT x.notes FROM supplier_price_lists x
                            WHERE x.product_id=product_suppliers.product_id
                              AND x.supplier_id=product_suppliers.supplier_id
                            ORDER BY CASE
                            WHEN warestat_date(x.validity_start_date) <= date('now')
                             AND (x.validity_end_date IS NULL OR warestat_date(x.validity_end_date) >= date('now'))
                            THEN 0 ELSE 1 END,
                            x.validity_start_date DESC, x.id DESC LIMIT 1
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
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_lots l
                      WHERE l.product_id=p.id AND l.source_type='MIGRATION'
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

    private static void releaseNewOrderReservations(Connection c) throws SQLException {
        if (!hasTable(c, "stock_reservations") || !hasTable(c, "orders")) return;
        try (PreparedStatement p = c.prepareStatement("""
                UPDATE stock_reservations
                SET status='CANCELLED'
                WHERE status='ACTIVE'
                  AND document_type='ORDER'
                  AND document_id IN (
                      SELECT id FROM orders WHERE status='New'
                  )
                """)) {
            p.executeUpdate();
        }
    }

    private static void migrateCustomerOrderReservationStates(Connection c) throws SQLException {
        if (!hasTable(c, "orders") || !hasTable(c, "order_details")
                || !hasTable(c, "stock_reservations")) return;

        // Migration 018 intentionally released all legacy New reservations. Rebuild them
        // only when the full order can be covered; otherwise reclassify as Reservation.
        StockManager.reconcileReservedStock(c);

        java.util.List<Integer> orderIds = new java.util.ArrayList<>();
        try (PreparedStatement orders = c.prepareStatement("""
                SELECT id
                FROM orders
                WHERE status='New'
                ORDER BY order_date, id
                """);
             ResultSet orderRows = orders.executeQuery()) {
            while (orderRows.next()) orderIds.add(orderRows.getInt("id"));
        }

        for (int orderId : orderIds) {
            java.util.List<StockManager.StockItem> items = new java.util.ArrayList<>();

            try (PreparedStatement details = c.prepareStatement("""
                    SELECT od.product_id, p.name, SUM(od.quantity) AS quantity
                    FROM order_details od
                    JOIN products p ON p.id=od.product_id
                    WHERE od.order_id=?
                    GROUP BY od.product_id, p.name
                    """)) {
                details.setInt(1, orderId);
                try (ResultSet rows = details.executeQuery()) {
                    while (rows.next()) {
                        items.add(new StockManager.StockItem(
                            rows.getInt("product_id"),
                            rows.getString("name"),
                            rows.getInt("quantity")));
                    }
                }
            }

            if (items.isEmpty()) continue;

            java.util.Map<String, StockManager.StockAvailability> insufficient =
                StockManager.checkStockAvailability(c, items, orderId, "ORDER");

            if (!insufficient.isEmpty()) {
                StockManager.cancelReservation(c, "ORDER", orderId);
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE orders SET status='Reservation' WHERE id=? AND status='New'")) {
                    update.setInt(1, orderId);
                    update.executeUpdate();
                }
            } else {
                for (StockManager.StockItem item : items) {
                    StockManager.createOrUpdateReservation(c, item.getProductId(),
                        "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
                }
            }

            // Keep the cached reserved quantity correct even when migrating a database
            // whose reservation triggers have not yet been recreated.
            StockManager.reconcileReservedStock(c);
        }
    }

    private static void migrateCustomerOrderCompletionDate(Connection c) throws SQLException {
        if (!hasTable(c, "orders")) return;
        addColumnIfMissing(c, "orders", "completed_date",
            "ALTER TABLE orders ADD COLUMN completed_date DATETIME");
        try (PreparedStatement p = c.prepareStatement("""
                UPDATE orders
                SET completed_date=order_date
                WHERE completed_date IS NULL
                  AND status IN ('Completed','Reversed')
                """)) {
            p.executeUpdate();
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

