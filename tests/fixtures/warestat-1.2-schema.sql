CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT NOT NULL,
                last_name TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                address TEXT
            );

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
            );

CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                order_date DATETIME NOT NULL,
                status TEXT NOT NULL,
                total REAL NOT NULL,
                FOREIGN KEY (customer_id) REFERENCES customers (id)
            );

CREATE TABLE IF NOT EXISTS order_details (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER,
                product_id INTEGER,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders (id),
                FOREIGN KEY (product_id) REFERENCES products (id)
            );

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
            );

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
            );

CREATE TABLE IF NOT EXISTS invoice_numbering (
                year INTEGER PRIMARY KEY,
                last_number INTEGER NOT NULL
            );

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
            );

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
            );

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
            );

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
            );

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
            );

CREATE TABLE IF NOT EXISTS minimum_stock (
                product_id INTEGER PRIMARY KEY,
                minimum_quantity INTEGER NOT NULL,
                reorder_quantity INTEGER NOT NULL,
                lead_time_days INTEGER,
                preferred_supplier_id INTEGER,
                notes TEXT,
                FOREIGN KEY (product_id) REFERENCES products (id),
                FOREIGN KEY (preferred_supplier_id) REFERENCES suppliers (id)
            );

CREATE TABLE IF NOT EXISTS warehouse_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                type TEXT NOT NULL,
                message TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'NEW',
                FOREIGN KEY (product_id) REFERENCES products (id)
            );

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
            );

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
            );

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
                    );
