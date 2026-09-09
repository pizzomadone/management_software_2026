/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

import java.sql.*;
import javax.swing.JOptionPane;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_URL;

    // Static initializer to set up the database URL
    static {
        DB_URL = "jdbc:sqlite:" + AppConstants.getDatabasePath().toString();
        System.out.println("Database will be created at: " + AppConstants.getDatabasePath());
    }

    private DatabaseManager() {
        // Private constructor for the Singleton pattern
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized void initDatabase() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Validate an existing archive before creating any missing application tables.
            boolean existing = java.nio.file.Files.exists(AppConstants.getDatabasePath());
            closeConnection();
            connection = openConnection(DB_URL);
            if (existing) DatabaseIntegrity.validate(connection, true);

            // Enable foreign keys and set SQLite optimizations
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = FULL");
                stmt.execute("PRAGMA busy_timeout = 10000");
            }

            // Create tables if they do not exist
            createTables();
            DatabaseIntegrity.validate(connection, false);
            DatabaseCurrency.bind(connection);

            System.out.println("Database initialized successfully");

        } catch (Exception e) {
            closeConnection();
            throw new IllegalStateException("Error during database initialization: " + e.getMessage(), e);
        }
    }

    public static void beginTransaction(Connection c) throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("An operation is already in progress on this connection");
        try {c.setAutoCommit(false);}
        catch(SQLException e) {
            // sqlite-jdbc may retain autocommit=false after a failed BEGIN. Never reuse that state.
            try {c.close();}catch(SQLException close){e.addSuppressed(close);}
            throw e;
        }
    }

    public static Connection openConnection(String url) throws SQLException {
        org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
        settings.enforceForeignKeys(true);
        settings.setBusyTimeout(10000);
        settings.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection c=DriverManager.getConnection(url, settings.toProperties());
        try {DateUtils.installSqlFunctions(c);return c;}catch(SQLException e){c.close();throw e;}
    }

    /** Migrate an isolated staging copy before it is allowed to replace live data. */
    public static void prepareDatabase(Connection conn) throws SQLException {
        DatabaseIntegrity.validate(conn, true);
        DatabaseManager staging = new DatabaseManager();
        staging.connection = conn;
        staging.createTables();
        DatabaseIntegrity.validate(conn, false);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            s.execute("PRAGMA journal_mode=DELETE");
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
                order_id INTEGER,
                FOREIGN KEY (order_id) REFERENCES orders (id),
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

        // Apply ordered, versioned schema migrations for existing databases.
        DatabaseMigrationManager.migrate(connection);

        // Create triggers for stock reservation synchronization
        createStockReservationTriggers();
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
                connection = null;
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to close the database", e);
        }
    }

    public String getNextInvoiceNumber(int year) throws SQLException {
        String number;
        Connection conn = getConnection(); // Use the safe getConnection method
        DatabaseManager.beginTransaction(conn);
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

    public String getNextInvoiceNumber(Connection conn, int year) throws SQLException {
        int nextNumber;
        String checkQuery = "SELECT last_number FROM invoice_numbering WHERE year = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
            pstmt.setInt(1, year);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    nextNumber = rs.getInt("last_number") + 1;
                    try (PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE invoice_numbering SET last_number = ? WHERE year = ?")) {
                        updateStmt.setInt(1, nextNumber);
                        updateStmt.setInt(2, year);
                        updateStmt.executeUpdate();
                    }
                } else {
                    nextNumber = 1;
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO invoice_numbering (year, last_number) VALUES (?, ?)")) {
                        insertStmt.setInt(1, year);
                        insertStmt.setInt(2, nextNumber);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
        return String.format("%d/%04d", year, nextNumber);
    }
}
