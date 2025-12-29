-- Script to populate the management software database with dummy but valid data
-- IMPORTANT: This script must be run with foreign keys disabled at the database level
-- If using SQLite Browser or similar tool, ensure foreign keys are disabled in settings

PRAGMA foreign_keys = OFF;
PRAGMA synchronous = OFF;
PRAGMA journal_mode = MEMORY;

-- Use a transaction to ensure all operations are atomic
BEGIN TRANSACTION;

-- Clear existing data (in reverse order of foreign key dependencies)
-- Child tables first, parent tables last
DELETE FROM warehouse_notifications;
DELETE FROM warehouse_movements;
DELETE FROM minimum_stock;
DELETE FROM supplier_price_lists;
DELETE FROM supplier_order_details;
DELETE FROM supplier_orders;
DELETE FROM invoice_details;
DELETE FROM invoices;
DELETE FROM invoice_numbering;
DELETE FROM order_details;
DELETE FROM orders;
DELETE FROM products;
DELETE FROM suppliers;
DELETE FROM customers;
DELETE FROM stock_reservations;
DELETE FROM company_data;

-- Commit the delete transaction
COMMIT;

-- Start new transaction for inserts
BEGIN TRANSACTION;

-- 1. CUSTOMERS (1000 records)
INSERT INTO customers (first_name, last_name, email, phone, address)
SELECT
    CASE (ABS(RANDOM()) % 20)
        WHEN 0 THEN 'John' WHEN 1 THEN 'Michael' WHEN 2 THEN 'David'
        WHEN 3 THEN 'James' WHEN 4 THEN 'Robert' WHEN 5 THEN 'William'
        WHEN 6 THEN 'Richard' WHEN 7 THEN 'Joseph' WHEN 8 THEN 'Thomas'
        WHEN 9 THEN 'Charles' WHEN 10 THEN 'Mary' WHEN 11 THEN 'Patricia'
        WHEN 12 THEN 'Jennifer' WHEN 13 THEN 'Linda' WHEN 14 THEN 'Elizabeth'
        WHEN 15 THEN 'Barbara' WHEN 16 THEN 'Susan' WHEN 17 THEN 'Jessica'
        WHEN 18 THEN 'Sarah' ELSE 'Karen'
    END,
    CASE (ABS(RANDOM()) % 25)
        WHEN 0 THEN 'Smith' WHEN 1 THEN 'Johnson' WHEN 2 THEN 'Williams'
        WHEN 3 THEN 'Brown' WHEN 4 THEN 'Jones' WHEN 5 THEN 'Garcia'
        WHEN 6 THEN 'Miller' WHEN 7 THEN 'Davis' WHEN 8 THEN 'Rodriguez'
        WHEN 9 THEN 'Martinez' WHEN 10 THEN 'Hernandez' WHEN 11 THEN 'Lopez'
        WHEN 12 THEN 'Gonzalez' WHEN 13 THEN 'Wilson' WHEN 14 THEN 'Anderson'
        WHEN 15 THEN 'Thomas' WHEN 16 THEN 'Taylor' WHEN 17 THEN 'Moore'
        WHEN 18 THEN 'Jackson' WHEN 19 THEN 'Martin' WHEN 20 THEN 'Lee'
        WHEN 21 THEN 'Thompson' WHEN 22 THEN 'White' WHEN 23 THEN 'Harris'
        ELSE 'Clark'
    END,
    'customer' || ABS(RANDOM()) || '@email.com',
    '+1 555-' || printf('%04d', 1000 + ABS(RANDOM() % 8999)),
    CASE (ABS(RANDOM()) % 10)
        WHEN 0 THEN 'Main Street' WHEN 1 THEN 'Oak Avenue' WHEN 2 THEN 'Park Road'
        WHEN 3 THEN 'Maple Drive' WHEN 4 THEN 'Cedar Lane' WHEN 5 THEN 'Pine Street'
        WHEN 6 THEN 'Washington Blvd' WHEN 7 THEN 'Lincoln Avenue' WHEN 8 THEN 'Jefferson Road'
        ELSE 'Madison Street'
    END || ' ' || (1 + ABS(RANDOM() % 999)) || ', New York, NY'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 1000
    )
    SELECT x FROM numbers
);

-- 2. SUPPLIERS (50 records)
INSERT INTO suppliers (company_name, vat_number, tax_code, address, phone, email, certified_email, website, notes)
SELECT
    CASE (ABS(RANDOM()) % 10)
        WHEN 0 THEN 'Tech' WHEN 1 THEN 'Global' WHEN 2 THEN 'Prime'
        WHEN 3 THEN 'Elite' WHEN 4 THEN 'Pro' WHEN 5 THEN 'Master'
        WHEN 6 THEN 'Supreme' WHEN 7 THEN 'Ultra' WHEN 8 THEN 'Mega'
        ELSE 'Super'
    END || ' Supplier ' || x || ' Inc.',
    printf('VAT%011d', ABS(RANDOM()) % 100000000000),
    printf('TAX%016d', ABS(RANDOM()) % 10000000000000000),
    'Industrial Park ' || (1 + ABS(RANDOM() % 50)) || ', Business District, CA',
    '+1 800-' || printf('%04d', 1000 + ABS(RANDOM() % 8999)),
    'info@supplier' || x || '.com',
    'certified@supplier' || x || '.com',
    'www.supplier' || x || '.com',
    'Reliable supplier with excellent service'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 50
    )
    SELECT x FROM numbers
);

-- 3. PRODUCTS (500 records)
INSERT INTO products (code, name, description, price, quantity, reserved_quantity, category, alternative_sku, weight, unit_of_measure, minimum_quantity, acquisition_cost, active, supplier_id, warehouse_position, vat_rate)
SELECT
    'PROD' || printf('%06d', x),
    CASE (ABS(RANDOM()) % 25)
        WHEN 0 THEN 'Resistor' WHEN 1 THEN 'Capacitor' WHEN 2 THEN 'Transistor'
        WHEN 3 THEN 'LED Diode' WHEN 4 THEN 'Integrated Circuit' WHEN 5 THEN 'Connector'
        WHEN 6 THEN 'USB Cable' WHEN 7 THEN 'Power Supply' WHEN 8 THEN 'Sensor'
        WHEN 9 THEN 'LCD Display' WHEN 10 THEN 'Speaker' WHEN 11 THEN 'Microphone'
        WHEN 12 THEN 'Switch' WHEN 13 THEN 'Motor' WHEN 14 THEN 'Keyboard'
        WHEN 15 THEN 'Mouse' WHEN 16 THEN 'Monitor' WHEN 17 THEN 'Router'
        WHEN 18 THEN 'Smartphone' WHEN 19 THEN 'Tablet' WHEN 20 THEN 'Laptop'
        WHEN 21 THEN 'Hard Drive' WHEN 22 THEN 'Memory Card' WHEN 23 THEN 'Battery'
        ELSE 'Adapter'
    END || ' Professional',
    'High quality product for professional use',
    ROUND(10 + (ABS(RANDOM() % 990)), 2),  -- price
    ABS(RANDOM() % 500),  -- quantity
    ABS(RANDOM() % 50),   -- reserved_quantity
    CASE (ABS(RANDOM()) % 8)
        WHEN 0 THEN 'Electronics' WHEN 1 THEN 'Components' WHEN 2 THEN 'Cables'
        WHEN 3 THEN 'Accessories' WHEN 4 THEN 'Hardware' WHEN 5 THEN 'Peripherals'
        WHEN 6 THEN 'Computing' ELSE 'Networking'
    END,
    'ALT-' || printf('%06d', x),  -- alternative_sku
    ROUND(0.1 + (ABS(RANDOM() % 100) / 10.0), 2),  -- weight in kg
    CASE (ABS(RANDOM()) % 3) WHEN 0 THEN 'pcs' WHEN 1 THEN 'box' ELSE 'pack' END,
    5 + ABS(RANDOM() % 20),  -- minimum_quantity
    ROUND(5 + (ABS(RANDOM() % 500)), 2),  -- acquisition_cost (lower than price)
    1,  -- active
    1 + ABS(RANDOM() % 50),  -- supplier_id
    CASE (ABS(RANDOM()) % 5)
        WHEN 0 THEN 'A' WHEN 1 THEN 'B' WHEN 2 THEN 'C' WHEN 3 THEN 'D' ELSE 'E'
    END || '-' || printf('%02d', 1 + ABS(RANDOM() % 99)),  -- warehouse_position
    22.0  -- vat_rate
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 500
    )
    SELECT x FROM numbers
);

-- 4. ORDERS (2000 records)
INSERT INTO orders (customer_id, order_date, status, total)
SELECT
    1 + ABS(RANDOM() % 1000),
    datetime('now', '-' || ABS(RANDOM() % 365) || ' days',
             '-' || ABS(RANDOM() % 24) || ' hours',
             '-' || ABS(RANDOM() % 60) || ' minutes'),
    CASE (ABS(RANDOM()) % 10)
        WHEN 0 THEN 'New' WHEN 1 THEN 'New'
        WHEN 2 THEN 'Processing' WHEN 3 THEN 'Processing'
        WHEN 4 THEN 'Completed' WHEN 5 THEN 'Completed' WHEN 6 THEN 'Completed' WHEN 7 THEN 'Completed'
        WHEN 8 THEN 'Cancelled' ELSE 'Shipped'
    END,
    0  -- will be updated later
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 2000
    )
    SELECT x FROM numbers
);

-- 5. ORDER DETAILS (5000 records)
INSERT INTO order_details (order_id, product_id, quantity, unit_price)
SELECT
    1 + ABS(RANDOM() % 2000),
    1 + ABS(RANDOM() % 500),
    1 + ABS(RANDOM() % 10),
    ROUND(10 + (ABS(RANDOM() % 90)), 2)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 5000
    )
    SELECT x FROM numbers
);

-- Update order totals
UPDATE orders SET total = (
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    FROM order_details WHERE order_id = orders.id
);

-- 6. INVOICES (1500 records)
DELETE FROM invoice_numbering;
INSERT INTO invoice_numbering (year, last_number) VALUES (2024, 1500);
INSERT INTO invoice_numbering (year, last_number) VALUES (2025, 0);

INSERT INTO invoices (number, date, customer_id, taxable_amount, vat, total, status)
SELECT
    '2024/' || printf('%04d', x),
    datetime('now', '-' || ABS(RANDOM() % 365) || ' days',
             '-' || ABS(RANDOM() % 24) || ' hours'),
    1 + ABS(RANDOM() % 1000),
    ROUND(100 + (ABS(RANDOM() % 900)), 2),
    ROUND((100 + (ABS(RANDOM() % 900))) * 0.22, 2),
    ROUND((100 + (ABS(RANDOM() % 900))) * 1.22, 2),
    CASE (ABS(RANDOM()) % 10)
        WHEN 0 THEN 'Draft' WHEN 1 THEN 'Draft'
        WHEN 2 THEN 'Issued' WHEN 3 THEN 'Issued'
        WHEN 4 THEN 'Paid' WHEN 5 THEN 'Paid' WHEN 6 THEN 'Paid' WHEN 7 THEN 'Paid'
        ELSE 'Overdue'
    END
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 1500
    )
    SELECT x FROM numbers
);

-- 7. INVOICE DETAILS (3500 records)
INSERT INTO invoice_details (invoice_id, product_id, quantity, unit_price, vat_rate, total)
SELECT
    1 + ABS(RANDOM() % 1500),
    1 + ABS(RANDOM() % 500),
    1 + ABS(RANDOM() % 10),
    ROUND(10 + (ABS(RANDOM() % 90)), 2),
    22.0,
    ROUND((1 + ABS(RANDOM() % 10)) * (10 + (ABS(RANDOM() % 90))), 2)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 3500
    )
    SELECT x FROM numbers
);

-- 8. SUPPLIER ORDERS (300 records)
INSERT INTO supplier_orders (supplier_id, number, order_date, expected_delivery_date, status, total, notes)
SELECT
    1 + ABS(RANDOM() % 50),
    'PO-2024-' || printf('%04d', x),
    datetime('now', '-' || ABS(RANDOM() % 180) || ' days'),
    datetime('now', '-' || ABS(RANDOM() % 180) || ' days', '+' || (7 + ABS(RANDOM() % 21)) || ' days'),
    CASE (ABS(RANDOM()) % 5)
        WHEN 0 THEN 'Pending' WHEN 1 THEN 'Confirmed'
        WHEN 2 THEN 'Shipped' WHEN 3 THEN 'Received'
        ELSE 'Cancelled'
    END,
    ROUND(500 + (ABS(RANDOM() % 4500)), 2),
    'Standard purchase order'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 300
    )
    SELECT x FROM numbers
);

-- 9. SUPPLIER ORDER DETAILS (800 records)
INSERT INTO supplier_order_details (order_id, product_id, quantity, unit_price, total, notes)
SELECT
    1 + ABS(RANDOM() % 300),
    1 + ABS(RANDOM() % 500),
    10 + ABS(RANDOM() % 90),
    ROUND(5 + (ABS(RANDOM() % 45)), 2),
    ROUND((10 + ABS(RANDOM() % 90)) * (5 + (ABS(RANDOM() % 45))), 2),
    'Bulk order'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 800
    )
    SELECT x FROM numbers
);

-- 10. SUPPLIER PRICE LISTS (1000 records)
INSERT INTO supplier_price_lists (supplier_id, product_id, price, valid_from, notes)
SELECT
    1 + ABS(RANDOM() % 50),
    1 + ABS(RANDOM() % 500),
    ROUND(5 + (ABS(RANDOM() % 45)), 2),
    datetime('now', '-' || ABS(RANDOM() % 365) || ' days'),
    'Current supplier price'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 1000
    )
    SELECT x FROM numbers
);

-- 11. MINIMUM STOCK (250 records) - Link to products table
INSERT INTO minimum_stock (product_id, minimum_quantity, reorder_quantity, notes)
SELECT
    x,
    5 + ABS(RANDOM() % 20),
    20 + ABS(RANDOM() % 80),
    'Strategic product - maintain stock levels'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 250
    )
    SELECT x FROM numbers
);

-- 12. WAREHOUSE MOVEMENTS (4000 records)
INSERT INTO warehouse_movements (product_id, type, quantity, date, reason, document_number, document_type, notes)
SELECT
    1 + ABS(RANDOM() % 500),
    CASE (ABS(RANDOM()) % 6)
        WHEN 0 THEN 'IN' WHEN 1 THEN 'IN' WHEN 2 THEN 'IN'
        WHEN 3 THEN 'OUT' WHEN 4 THEN 'OUT' ELSE 'ADJUSTMENT'
    END,
    1 + ABS(RANDOM() % 50),
    datetime('now', '-' || ABS(RANDOM() % 180) || ' days',
             '-' || ABS(RANDOM() % 24) || ' hours'),
    CASE (ABS(RANDOM()) % 5)
        WHEN 0 THEN 'PURCHASE' WHEN 1 THEN 'SALE'
        WHEN 2 THEN 'ADJUSTMENT' WHEN 3 THEN 'RETURN'
        ELSE 'TRANSFER'
    END,
    'DOC-' || printf('%06d', 10000 + ABS(RANDOM() % 89999)),
    CASE (ABS(RANDOM()) % 4)
        WHEN 0 THEN 'DDT' WHEN 1 THEN 'INVOICE'
        WHEN 2 THEN 'ORDER' ELSE 'INTERNAL'
    END,
    CASE (ABS(RANDOM()) % 5)
        WHEN 0 THEN 'Purchase order received'
        WHEN 1 THEN 'Sale to customer'
        WHEN 2 THEN 'Stock adjustment'
        WHEN 3 THEN 'Inventory count correction'
        ELSE 'Transfer between warehouses'
    END
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 4000
    )
    SELECT x FROM numbers
);

-- 13. WAREHOUSE NOTIFICATIONS (150 records)
INSERT INTO warehouse_notifications (product_id, date, type, message, status)
SELECT
    1 + ABS(RANDOM() % 500),
    datetime('now', '-' || ABS(RANDOM() % 30) || ' days'),
    CASE (ABS(RANDOM()) % 4)
        WHEN 0 THEN 'LOW_STOCK' WHEN 1 THEN 'OUT_OF_STOCK'
        WHEN 2 THEN 'REORDER_NEEDED' ELSE 'EXPIRING_SOON'
    END,
    CASE (ABS(RANDOM()) % 4)
        WHEN 0 THEN 'Stock level below minimum threshold. Reorder required.'
        WHEN 1 THEN 'Product out of stock. Immediate action needed.'
        WHEN 2 THEN 'Reorder point reached. Place order with supplier.'
        ELSE 'Product approaching expiration date.'
    END,
    CASE (ABS(RANDOM()) % 3)
        WHEN 0 THEN 'NEW' WHEN 1 THEN 'READ' ELSE 'HANDLED'
    END
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 150
    )
    SELECT x FROM numbers
);

-- Update invoice totals based on details
UPDATE invoices SET
    taxable_amount = (
        SELECT COALESCE(SUM(total), 0)
        FROM invoice_details WHERE invoice_id = invoices.id
    ),
    vat = (
        SELECT COALESCE(SUM(total * vat_rate / 100), 0)
        FROM invoice_details WHERE invoice_id = invoices.id
    ),
    total = (
        SELECT COALESCE(SUM(total * (1 + vat_rate / 100)), 0)
        FROM invoice_details WHERE invoice_id = invoices.id
    )
WHERE id IN (SELECT DISTINCT invoice_id FROM invoice_details);

-- Update supplier order totals
UPDATE supplier_orders SET total = (
    SELECT COALESCE(SUM(total), 0)
    FROM supplier_order_details WHERE order_id = supplier_orders.id
);

-- Commit all insert operations
COMMIT;

-- Re-enable foreign keys and restore settings
PRAGMA foreign_keys = ON;
PRAGMA synchronous = NORMAL;
PRAGMA journal_mode = WAL;

-- Display statistics
SELECT '=== DATABASE POPULATION COMPLETE ===' as result
UNION ALL SELECT ''
UNION ALL SELECT 'Customers: ' || COUNT(*) FROM customers
UNION ALL SELECT 'Suppliers: ' || COUNT(*) FROM suppliers
UNION ALL SELECT 'Products: ' || COUNT(*) FROM products
UNION ALL SELECT 'Orders: ' || COUNT(*) FROM orders
UNION ALL SELECT 'Order Details: ' || COUNT(*) FROM order_details
UNION ALL SELECT 'Invoices: ' || COUNT(*) FROM invoices
UNION ALL SELECT 'Invoice Details: ' || COUNT(*) FROM invoice_details
UNION ALL SELECT 'Supplier Orders: ' || COUNT(*) FROM supplier_orders
UNION ALL SELECT 'Supplier Order Details: ' || COUNT(*) FROM supplier_order_details
UNION ALL SELECT 'Supplier Price Lists: ' || COUNT(*) FROM supplier_price_lists
UNION ALL SELECT 'Minimum Stock: ' || COUNT(*) FROM minimum_stock
UNION ALL SELECT 'Warehouse Movements: ' || COUNT(*) FROM warehouse_movements
UNION ALL SELECT 'Warehouse Notifications: ' || COUNT(*) FROM warehouse_notifications
UNION ALL SELECT ''
UNION ALL SELECT '=== READY FOR USE ===';
