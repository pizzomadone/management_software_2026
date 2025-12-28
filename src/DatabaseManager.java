import java.sql.*;
import javax.swing.JOptionPane;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:gestionale.db";

    private DatabaseManager() {
        // Private constructor for the Singleton pattern
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initDatabase() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create the connection
            connection = DriverManager.getConnection(DB_URL);

            // Enable foreign keys and set SQLite optimizations
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
            }

            // Create tables if they do not exist
            createTables();

            System.out.println("Database initialized successfully");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                "Error during database initialization: " + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createTables() throws SQLException {
        // Customers Table
        String createCustomersTable = """
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT NOT NULL,
                last_name TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                address TEXT
            )
        """;

        // Products Table
        String createProductsTable = """
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                price REAL NOT NULL,
                quantity INTEGER DEFAULT 0,
                reserved_quantity INTEGER DEFAULT 0,
                category TEXT DEFAULT '',
                alternative_sku TEXT DEFAULT '',
                weight REAL DEFAULT 0.0,
                unit_of_measure TEXT DEFAULT 'pcs',
                minimum_quantity INTEGER DEFAULT 0,
                acquisition_cost REAL DEFAULT 0.0,
                active INTEGER DEFAULT 1,
                supplier_id INTEGER,
                warehouse_position TEXT DEFAULT '',
                vat_rate REAL DEFAULT 0.0,
                FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
            )
        """;

        // Orders Table - FIXED: Changed to DATETIME
        String createOrdersTable = """
            CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                order_date DATETIME NOT NULL,
                status TEXT NOT NULL,
                total REAL NOT NULL,
                FOREIGN KEY (customer_id) REFERENCES customers (id)
            )
        """;

        // Order Details Table
        String createOrderDetailsTable = """
            CREATE TABLE IF NOT EXISTS order_details (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER,
                product_id INTEGER,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders (id),
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Invoices Table - FIXED: Changed to DATETIME
        String createInvoicesTable = """
            CREATE TABLE IF NOT EXISTS invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT UNIQUE NOT NULL,
                date DATETIME NOT NULL,
                customer_id INTEGER,
                taxable_amount REAL NOT NULL,
                vat REAL NOT NULL,
                total REAL NOT NULL,
                status TEXT NOT NULL,
                FOREIGN KEY (customer_id) REFERENCES customers (id)
            )
        """;

        // Invoice Details Table
        String createInvoiceDetailsTable = """
            CREATE TABLE IF NOT EXISTS invoice_details (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoice_id INTEGER,
                product_id INTEGER,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                vat_rate REAL NOT NULL,
                total REAL NOT NULL,
                FOREIGN KEY (invoice_id) REFERENCES invoices (id),
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Invoice Numbering Table
        String createInvoiceNumberingTable = """
            CREATE TABLE IF NOT EXISTS invoice_numbering (
                year INTEGER PRIMARY KEY,
                last_number INTEGER NOT NULL
            )
        """;

        // Suppliers Table
        String createSuppliersTable = """
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                company_name TEXT NOT NULL,
                vat_number TEXT UNIQUE NOT NULL,
                tax_code TEXT,
                address TEXT,
                phone TEXT,
                email TEXT,
                certified_email TEXT,
                website TEXT,
                notes TEXT
            )
        """;

        // Supplier Orders Table - FIXED: Changed to DATETIME
        String createSupplierOrdersTable = """
            CREATE TABLE IF NOT EXISTS supplier_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER NOT NULL,
                number TEXT NOT NULL,
                order_date DATETIME NOT NULL,
                expected_delivery_date DATETIME,
                status TEXT NOT NULL,
                total REAL NOT NULL,
                notes TEXT,
                FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
            )
        """;

        // Supplier Order Details Table
        String createSupplierOrderDetailsTable = """
            CREATE TABLE IF NOT EXISTS supplier_order_details (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                total REAL NOT NULL,
                notes TEXT,
                FOREIGN KEY (order_id) REFERENCES supplier_orders (id),
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Supplier Price Lists Table - FIXED: Changed to DATETIME
        String createSupplierPriceListsTable = """
            CREATE TABLE IF NOT EXISTS supplier_price_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                supplier_product_code TEXT,
                price REAL NOT NULL,
                minimum_quantity INTEGER DEFAULT 1,
                validity_start_date DATETIME NOT NULL,
                validity_end_date DATETIME,
                notes TEXT,
                FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Warehouse Movements Table - FIXED: Changed to DATETIME
        String createWarehouseMovementsTable = """
            CREATE TABLE IF NOT EXISTS warehouse_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                type TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                reason TEXT NOT NULL,
                document_number TEXT,
                document_type TEXT,
                notes TEXT,
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Minimum Stock Table
        String createMinimumStockTable = """
            CREATE TABLE IF NOT EXISTS minimum_stock (
                product_id INTEGER PRIMARY KEY,
                minimum_quantity INTEGER NOT NULL,
                reorder_quantity INTEGER NOT NULL,
                lead_time_days INTEGER,
                preferred_supplier_id INTEGER,
                notes TEXT,
                FOREIGN KEY (product_id) REFERENCES products (id),
                FOREIGN KEY (preferred_supplier_id) REFERENCES suppliers (id)
            )
        """;

        // Warehouse Notifications Table - FIXED: Changed to DATETIME
        String createWarehouseNotificationsTable = """
            CREATE TABLE IF NOT EXISTS warehouse_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                type TEXT NOT NULL,
                message TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'NEW',
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Stock Reservations Table
        String createStockReservationsTable = """
            CREATE TABLE IF NOT EXISTS stock_reservations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                document_type TEXT NOT NULL,
                document_id INTEGER NOT NULL,
                reserved_quantity INTEGER NOT NULL,
                reservation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                notes TEXT,
                FOREIGN KEY (product_id) REFERENCES products (id)
            )
        """;

        // Company Data Table
        String createCompanyDataTable = """
            CREATE TABLE IF NOT EXISTS company_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                company_name TEXT NOT NULL,
                vat_number TEXT,
                tax_code TEXT,
                address TEXT,
                city TEXT,
                postal_code TEXT,
                country TEXT,
                phone TEXT,
                email TEXT,
                website TEXT,
                logo_path TEXT
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createCustomersTable);
            stmt.execute(createSuppliersTable); // Create suppliers table first
            stmt.execute(createProductsTable);
            stmt.execute(createOrdersTable);
            stmt.execute(createOrderDetailsTable);
            stmt.execute(createInvoicesTable);
            stmt.execute(createInvoiceDetailsTable);
            stmt.execute(createInvoiceNumberingTable);
            stmt.execute(createSupplierOrdersTable);
            stmt.execute(createSupplierOrderDetailsTable);
            stmt.execute(createSupplierPriceListsTable);
            stmt.execute(createWarehouseMovementsTable);
            stmt.execute(createMinimumStockTable);
            stmt.execute(createWarehouseNotificationsTable);
            stmt.execute(createStockReservationsTable);
            stmt.execute(createCompanyDataTable);
        }

        // Migrate existing data from supplier TEXT to supplier_id INTEGER
        migrateSupplierData();

        // Migrate existing databases to add reserved_quantity column
        migrateStockReservationData();

        // Migrate existing databases to add warehouse_position and vat_rate columns
        migrateWarehousePositionAndVat();

        // Create triggers for stock reservation synchronization
        createStockReservationTriggers();
    }

    private void migrateSupplierData() throws SQLException {
        // Check if old 'supplier' column exists (TEXT type)
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(products)")) {

            boolean hasOldSupplierColumn = false;
            boolean hasNewSupplierIdColumn = false;

            while (rs.next()) {
                String columnName = rs.getString("name");
                String columnType = rs.getString("type");

                if ("supplier".equals(columnName) && "TEXT".equalsIgnoreCase(columnType)) {
                    hasOldSupplierColumn = true;
                } else if ("supplier_id".equals(columnName)) {
                    hasNewSupplierIdColumn = true;
                }
            }

            // If old column exists, we need to migrate
            if (hasOldSupplierColumn && !hasNewSupplierIdColumn) {
                System.out.println("Migrating supplier data from TEXT to INTEGER foreign key...");

                // Create temporary table with new schema
                String createTempTable = """
                    CREATE TABLE products_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT UNIQUE NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        price REAL NOT NULL,
                        quantity INTEGER DEFAULT 0,
                        category TEXT DEFAULT '',
                        alternative_sku TEXT DEFAULT '',
                        weight REAL DEFAULT 0.0,
                        unit_of_measure TEXT DEFAULT 'pcs',
                        minimum_quantity INTEGER DEFAULT 0,
                        acquisition_cost REAL DEFAULT 0.0,
                        active INTEGER DEFAULT 1,
                        supplier_id INTEGER,
                        FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
                    )
                """;

                stmt.execute(createTempTable);

                // Copy data and convert supplier names to IDs
                String copyData = """
                    INSERT INTO products_new
                    (id, code, name, description, price, quantity, category,
                     alternative_sku, weight, unit_of_measure, minimum_quantity,
                     acquisition_cost, active, supplier_id)
                    SELECT
                        p.id, p.code, p.name, p.description, p.price, p.quantity,
                        p.category, p.alternative_sku, p.weight, p.unit_of_measure,
                        p.minimum_quantity, p.acquisition_cost, p.active,
                        f.id as supplier_id
                    FROM products p
                    LEFT JOIN suppliers f ON p.supplier = f.company_name
                """;

                stmt.execute(copyData);

                // Drop old table and rename new one
                stmt.execute("DROP TABLE products");
                stmt.execute("ALTER TABLE products_new RENAME TO products");

                System.out.println("Supplier data migration completed successfully!");
            } else if (!hasOldSupplierColumn && !hasNewSupplierIdColumn) {
                // Brand new database, supplier_id already created correctly
                System.out.println("New database detected, no migration needed.");
            }
        }
    }

    private void migrateStockReservationData() throws SQLException {
        // Check if reserved_quantity column exists
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(products)")) {

            boolean hasReservedQuantity = false;

            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("reserved_quantity".equals(columnName)) {
                    hasReservedQuantity = true;
                    break;
                }
            }

            // If column doesn't exist, add it
            if (!hasReservedQuantity) {
                System.out.println("Adding reserved_quantity column to products table...");
                stmt.execute("ALTER TABLE products ADD COLUMN reserved_quantity INTEGER DEFAULT 0");
                System.out.println("Column reserved_quantity added successfully!");
            }
        }
    }

    private void migrateWarehousePositionAndVat() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(products)")) {

            boolean hasWarehousePosition = false;
            boolean hasVatRate = false;

            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("warehouse_position".equals(columnName)) {
                    hasWarehousePosition = true;
                } else if ("vat_rate".equals(columnName)) {
                    hasVatRate = true;
                }
            }

            // Add warehouse_position column if it doesn't exist
            if (!hasWarehousePosition) {
                System.out.println("Adding warehouse_position column to products table...");
                stmt.execute("ALTER TABLE products ADD COLUMN warehouse_position TEXT DEFAULT ''");
                System.out.println("Column warehouse_position added successfully!");
            }

            // Add vat_rate column if it doesn't exist
            if (!hasVatRate) {
                System.out.println("Adding vat_rate column to products table...");
                stmt.execute("ALTER TABLE products ADD COLUMN vat_rate REAL DEFAULT 0.0");
                System.out.println("Column vat_rate added successfully!");
            }
        }
    }

    private void createStockReservationTriggers() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Drop existing triggers if they exist
            stmt.execute("DROP TRIGGER IF EXISTS update_reserved_stock_insert");
            stmt.execute("DROP TRIGGER IF EXISTS update_reserved_stock_update");
            stmt.execute("DROP TRIGGER IF EXISTS update_reserved_stock_delete");

            // Trigger: When a new reservation is created with ACTIVE status
            String insertTrigger = """
                CREATE TRIGGER update_reserved_stock_insert
                AFTER INSERT ON stock_reservations
                WHEN NEW.status = 'ACTIVE'
                BEGIN
                    UPDATE products
                    SET reserved_quantity = reserved_quantity + NEW.reserved_quantity
                    WHERE id = NEW.product_id;
                END
            """;

            // Trigger: When a reservation status changes
            String updateTrigger = """
                CREATE TRIGGER update_reserved_stock_update
                AFTER UPDATE ON stock_reservations
                WHEN NEW.status != OLD.status OR NEW.reserved_quantity != OLD.reserved_quantity
                BEGIN
                    UPDATE products
                    SET reserved_quantity = reserved_quantity
                        - CASE WHEN OLD.status = 'ACTIVE' THEN OLD.reserved_quantity ELSE 0 END
                        + CASE WHEN NEW.status = 'ACTIVE' THEN NEW.reserved_quantity ELSE 0 END
                    WHERE id = NEW.product_id;
                END
            """;

            // Trigger: When a reservation is deleted
            String deleteTrigger = """
                CREATE TRIGGER update_reserved_stock_delete
                AFTER DELETE ON stock_reservations
                WHEN OLD.status = 'ACTIVE'
                BEGIN
                    UPDATE products
                    SET reserved_quantity = reserved_quantity - OLD.reserved_quantity
                    WHERE id = OLD.product_id;
                END
            """;

            stmt.execute(insertTrigger);
            stmt.execute(updateTrigger);
            stmt.execute(deleteTrigger);

            System.out.println("Stock reservation triggers created successfully!");
        }
    }

    public Connection getConnection() throws SQLException {
        // Check if connection is valid, if not recreate it
        if (connection == null || connection.isClosed() || !connection.isValid(5)) {
            initDatabase();
        }
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getNextInvoiceNumber(int year) throws SQLException {
        String number;
        Connection conn = getConnection(); // Use the safe getConnection method
        conn.setAutoCommit(false);
        try {
            // Check if a record for the current year already exists
            String checkQuery = "SELECT last_number FROM invoice_numbering WHERE year = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
                pstmt.setInt(1, year);
                ResultSet rs = pstmt.executeQuery();

                int nextNumber;
                if (rs.next()) {
                    // Increment the last number
                    nextNumber = rs.getInt("last_number") + 1;
                    String updateQuery = "UPDATE invoice_numbering SET last_number = ? WHERE year = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, nextNumber);
                        updateStmt.setInt(2, year);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Create a new record for the year
                    nextNumber = 1;
                    String insertQuery = "INSERT INTO invoice_numbering (year, last_number) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                        insertStmt.setInt(1, year);
                        insertStmt.setInt(2, nextNumber);
                        insertStmt.executeUpdate();
                    }
                }

                // Format the invoice number (e.g., 2024/0001)
                number = String.format("%d/%04d", year, nextNumber);
            }

            conn.commit();
            return number;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
