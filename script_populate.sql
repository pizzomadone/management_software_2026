-- WareStat test database population script
-- Target: current schema on feature/product-suppliers-fifo (migrations through 012)
--
-- IMPORTANT
-- 1) Start WareStat at least once so that the database and all migrations are created.
-- 2) Close WareStat before running this script.
-- 3) EVERY RUN IS A FULL TEST-DATA RESET.
--    All previous operational/test data is deleted first, AUTOINCREMENT counters
--    are reset, and the dataset is rebuilt from zero.
--    Only schema_migrations and the database currency in application_metadata
--    are preserved because they describe the database structure/configuration,
--    not test records.
-- 4) Product image rows are intentionally left empty because SQL cannot create the
--    corresponding JPEG files in WareStat/product_images. Add a few photos from the UI
--    when testing the image/backup workflow.
--
-- The generated data is deterministic enough to exercise:
-- - dashboard KPIs and sales/profit reports
-- - low stock, out of stock and negative stock cases
-- - products with zero acquisition cost
-- - active/inactive products and different VAT rates
-- - customer orders in every supported status/payment state
-- - active/completed/cancelled stock reservations
-- - invoices in Draft/Issued/Paid/Canceled states with immutable snapshots
-- - supplier orders, product/supplier prices and FIFO inventory lots
-- - warehouse movements and notifications
-- - long product codes/names/descriptions for UI testing

PRAGMA foreign_keys = OFF;

BEGIN IMMEDIATE;

-- ---------------------------------------------------------------------------
-- RESET OPERATIONAL DATA
-- FULL RESET: running this script twice must never append to the previous dataset.
-- The DELETE order follows foreign-key dependencies; ids are reset below.
-- ---------------------------------------------------------------------------
-- Reconcile reservation counters before changing reservation states so the
-- application's stock-reservation triggers cannot drive values below zero.
UPDATE products
SET reserved_quantity = COALESCE((
    SELECT SUM(r.reserved_quantity)
    FROM stock_reservations r
    WHERE r.product_id = products.id AND r.status = 'ACTIVE'
), 0);

UPDATE stock_reservations
SET status = 'CANCELLED'
WHERE status = 'ACTIVE';

DELETE FROM stock_reservations;
DELETE FROM inventory_lot_consumptions;
DELETE FROM inventory_lots;
DELETE FROM product_images;
DELETE FROM warehouse_notifications;
DELETE FROM warehouse_movements;
DELETE FROM minimum_stock;
DELETE FROM product_suppliers;
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
DELETE FROM company_data;

-- Reset AUTOINCREMENT ids for repeatable test data.
DELETE FROM sqlite_sequence
WHERE name IN (
    'customers','suppliers','products','product_images','orders','order_details',
    'invoices','invoice_details','supplier_orders','supplier_order_details',
    'product_suppliers','inventory_lots','inventory_lot_consumptions',
    'warehouse_movements','warehouse_notifications','stock_reservations','company_data'
);

-- The current schema always has application_metadata after migrations.
-- Keep an existing operating currency; provide EUR only as a defensive default.
INSERT OR IGNORE INTO application_metadata(key, value)
VALUES ('currency_code', 'EUR');

-- ---------------------------------------------------------------------------
-- COMPANY
-- ---------------------------------------------------------------------------
INSERT INTO company_data (
    company_name, vat_number, tax_code, address, city, postal_code, country,
    phone, email, website, logo_path
) VALUES (
    'WareStat Demo Company',
    'IT12345678901',
    'DMOCMP86A01H501Z',
    'Via del Magazzino 24',
    'Palermo',
    '90100',
    'Italy',
    '+39 091 555 0100',
    'amministrazione@demo.warestat.test',
    'https://www.warestat.com',
    ''
);

-- ---------------------------------------------------------------------------
-- CUSTOMERS: 120
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 120
)
INSERT INTO customers(first_name, last_name, email, phone, address)
SELECT
    CASE x % 12
        WHEN 0 THEN 'Andrea' WHEN 1 THEN 'Marco' WHEN 2 THEN 'Giulia'
        WHEN 3 THEN 'Francesca' WHEN 4 THEN 'Luca' WHEN 5 THEN 'Sara'
        WHEN 6 THEN 'Alessandro' WHEN 7 THEN 'Elena' WHEN 8 THEN 'Davide'
        WHEN 9 THEN 'Martina' WHEN 10 THEN 'Paolo' ELSE 'Chiara'
    END,
    CASE x % 12
        WHEN 0 THEN 'Rossi' WHEN 1 THEN 'Bianchi' WHEN 2 THEN 'Romano'
        WHEN 3 THEN 'Conti' WHEN 4 THEN 'Greco' WHEN 5 THEN 'Costa'
        WHEN 6 THEN 'Lombardi' WHEN 7 THEN 'Mancini' WHEN 8 THEN 'Gallo'
        WHEN 9 THEN 'Ferrari' WHEN 10 THEN 'Esposito' ELSE 'Ricci'
    END,
    printf('cliente%03d@demo.warestat.test', x),
    printf('+39 320 555 %04d', x),
    CASE x % 6
        WHEN 0 THEN 'Via Roma '
        WHEN 1 THEN 'Via Libertà '
        WHEN 2 THEN 'Corso Italia '
        WHEN 3 THEN 'Via Dante '
        WHEN 4 THEN 'Via Garibaldi '
        ELSE 'Viale Europa '
    END || (10 + x) || ', ' ||
    CASE x % 5
        WHEN 0 THEN 'Palermo'
        WHEN 1 THEN 'Catania'
        WHEN 2 THEN 'Messina'
        WHEN 3 THEN 'Caltanissetta'
        ELSE 'Agrigento'
    END
FROM seq;

-- ---------------------------------------------------------------------------
-- SUPPLIERS: 18
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 18
)
INSERT INTO suppliers(
    company_name, vat_number, tax_code, address, phone, email,
    certified_email, website, notes
)
SELECT
    CASE x % 6
        WHEN 0 THEN 'Sicilia Logistics'
        WHEN 1 THEN 'Tech Distribution'
        WHEN 2 THEN 'Office Supply Group'
        WHEN 3 THEN 'Industrial Components'
        WHEN 4 THEN 'Mediterranean Trade'
        ELSE 'Global Warehouse Supply'
    END || ' ' || printf('%02d', x),
    'IT' || printf('%011d', 10000000000 + x),
    'SUP' || printf('%013d', x),
    'Zona Industriale ' || x || ', ' ||
    CASE x % 4 WHEN 0 THEN 'Palermo' WHEN 1 THEN 'Catania'
               WHEN 2 THEN 'Milano' ELSE 'Bologna' END,
    printf('+39 091 700 %04d', x),
    printf('info%02d@fornitore.demo.warestat.test', x),
    printf('pec%02d@pec.demo.warestat.test', x),
    printf('https://supplier%02d.demo.warestat.test', x),
    CASE x % 4
        WHEN 0 THEN 'Preferred supplier - fast delivery'
        WHEN 1 THEN 'Competitive pricing for bulk orders'
        WHEN 2 THEN 'Specialized supplier for technical items'
        ELSE 'Secondary supplier used for price comparisons'
    END
FROM seq;

-- ---------------------------------------------------------------------------
-- PRODUCTS: 120
-- Includes deliberate edge cases for inventory/report testing.
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 120
)
INSERT INTO products(
    code, name, description, sale_price_ex_vat, sale_price_inc_vat, quantity, reserved_quantity, category,
    alternative_sku, weight, unit_of_measure, acquisition_cost, active,
    supplier_id, warehouse_position, vat_rate
)
SELECT
    CASE
        WHEN x % 23 = 0
            THEN 'LONG-SKU-CODE-' || printf('%05d', x) || '-WAREHOUSE'
        ELSE 'SKU-' || printf('%05d', x)
    END,
    CASE
        WHEN x % 19 = 0
            THEN 'Industrial high-capacity modular warehouse component with extended professional description ' || x
        WHEN x % 10 = 0 THEN 'Ergonomic wireless keyboard ' || x
        WHEN x % 10 = 1 THEN 'USB-C docking station ' || x
        WHEN x % 10 = 2 THEN 'Thermal label printer ' || x
        WHEN x % 10 = 3 THEN 'Barcode scanner ' || x
        WHEN x % 10 = 4 THEN 'Warehouse storage box ' || x
        WHEN x % 10 = 5 THEN 'Office chair ' || x
        WHEN x % 10 = 6 THEN 'Network switch ' || x
        WHEN x % 10 = 7 THEN 'Safety gloves pack ' || x
        WHEN x % 10 = 8 THEN 'Industrial sensor ' || x
        ELSE 'Packaging material roll ' || x
    END,
    CASE x % 6
        WHEN 0 THEN 'Fast-moving product used to test stock rotation and reorder calculations.'
        WHEN 1 THEN 'Product with a deliberately descriptive text for table truncation and tooltip testing.'
        WHEN 2 THEN 'Standard warehouse item supplied by multiple vendors.'
        WHEN 3 THEN 'Seasonal item with variable stock levels and demand.'
        WHEN 4 THEN 'Technical item used in customer and supplier order tests.'
        ELSE 'General inventory item for reports, invoices and dashboard statistics.'
    END,
    ROUND(9.90 + ((x * 37) % 850) / 2.7, 2),
    ROUND(9.90 + ((x * 37) % 850) / 2.7, 2),
    CASE
        WHEN x % 29 = 0 THEN -5
        WHEN x % 11 = 0 THEN 0
        WHEN x % 7 = 0 THEN 3
        ELSE 15 + ((x * 7) % 85)
    END,
    0,
    CASE x % 8
        WHEN 0 THEN 'Electronics'
        WHEN 1 THEN 'Computers & Accessories'
        WHEN 2 THEN 'Office Supplies'
        WHEN 3 THEN 'Industrial & Scientific'
        WHEN 4 THEN 'Tools & Hardware'
        WHEN 5 THEN 'Furniture'
        WHEN 6 THEN 'Clothing'
        ELSE 'Other'
    END,
    'ALT-' || printf('%05d', x),
    ROUND(0.10 + ((x * 13) % 250) / 10.0, 2),
    CASE x % 5
        WHEN 0 THEN 'pcs'
        WHEN 1 THEN 'box'
        WHEN 2 THEN 'pack'
        WHEN 3 THEN 'kg'
        ELSE 'm'
    END,
    CASE
        WHEN x % 17 = 0 THEN 0.0
        ELSE ROUND((9.90 + ((x * 37) % 850) / 2.7) * (0.42 + (x % 5) * 0.06), 2)
    END,
    CASE WHEN x % 20 = 0 THEN 0 ELSE 1 END,
    NULL,
    char(65 + ((x - 1) % 6)) || '-' ||
        printf('%02d', 1 + ((x * 3) % 30)) || '-S' || (1 + (x % 5)),
    CASE x % 5
        WHEN 0 THEN 22.0
        WHEN 1 THEN 10.0
        WHEN 2 THEN 5.0
        WHEN 3 THEN 0.0
        ELSE 22.0
    END
FROM seq;

-- The generated catalog value is the customer-facing VAT-inclusive price.
-- Keep the accounting net price derived from that exact gross value.
UPDATE products
SET sale_price_ex_vat = sale_price_inc_vat / (1.0 + vat_rate / 100.0);

-- Minimum/reorder settings for every product.
INSERT INTO minimum_stock(
    product_id, minimum_quantity, reorder_quantity,
    lead_time_days, preferred_supplier_id, notes
)
SELECT
    id,
    CASE WHEN id % 13 = 0 THEN 0 ELSE 5 + (id % 12) END,
    15 + (id % 45),
    2 + (id % 14),
    NULL,
    CASE
        WHEN id % 11 = 0 THEN 'Out-of-stock test case'
        WHEN id % 7 = 0 THEN 'Low-stock test case'
        ELSE 'Normal reorder policy'
    END
FROM products;

-- ---------------------------------------------------------------------------
-- PRODUCT / SUPPLIER RELATIONS: 360 (3 suppliers per product)
-- ---------------------------------------------------------------------------
-- Preferred supplier: current reference price initially matches the product's
-- last purchase cost. It remains independent from future negotiated order prices.
INSERT INTO product_suppliers(
    product_id,supplier_id,supplier_product_code,purchase_price,is_preferred,notes
)
SELECT
    p.id,1 + ((p.id - 1) % 18),
    'SUP-' || printf('%02d',1 + ((p.id - 1) % 18)) || '-' || p.code,
    ROUND(CASE WHEN p.acquisition_cost=0 THEN p.sale_price_ex_vat*0.45 ELSE p.acquisition_cost END,2),
    1,'Preferred supplier'
FROM products p;

-- Two alternative suppliers with independent current reference prices.
WITH alternatives(n) AS (VALUES(1),(2))
INSERT INTO product_suppliers(
    product_id,supplier_id,supplier_product_code,purchase_price,is_preferred,notes
)
SELECT
    p.id,
    1 + ((pref.supplier_id - 1 + alternatives.n * 5) % 18),
    'SUP-' || printf('%02d',1 + ((pref.supplier_id - 1 + alternatives.n * 5) % 18)) || '-' || p.code,
    ROUND(
        CASE WHEN p.acquisition_cost=0 THEN p.sale_price_ex_vat*0.45
             ELSE p.acquisition_cost*(0.94 + alternatives.n*0.04) END,2
    ),
    0,'Alternative supplier'
FROM products p
JOIN product_suppliers pref ON pref.product_id=p.id AND pref.is_preferred=1
CROSS JOIN alternatives;

-- Demo opening stock. This is the current economic inventory layer; historical
-- pre-script purchases are intentionally not fabricated.
INSERT INTO inventory_lots(
    product_id,supplier_id,received_date,initial_quantity,remaining_quantity,unit_cost,source_type
)
SELECT
    p.id,pref.supplier_id,datetime('now','-365 days'),
    p.quantity,p.quantity,p.acquisition_cost,'DEMO_OPENING'
FROM products p
LEFT JOIN product_suppliers pref ON pref.product_id=p.id AND pref.is_preferred=1
WHERE p.quantity>0;

-- ---------------------------------------------------------------------------
-- CUSTOMER ORDERS: 240 + 720 lines
-- Spread across roughly 30 months so the 6/12/24-month statistics filters
-- produce visibly different results.
-- Supported statuses: New / In Progress / Completed / Cancelled
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 240
)
INSERT INTO orders(
    customer_id, order_date, status, total_ex_vat, total_inc_vat, payment_status, paid_amount
)
SELECT
    1 + ((x * 7) % 120),
    datetime('now', '-' || ((x * 17) % 900) || ' days', '-' || (x % 10) || ' hours'),
    CASE x % 4
        WHEN 0 THEN 'New'
        WHEN 1 THEN 'In Progress'
        WHEN 2 THEN 'Completed'
        ELSE 'Cancelled'
    END,
    0,
    0,
    CASE x % 3
        WHEN 0 THEN 'NOT_PAID'
        WHEN 1 THEN 'PARTIALLY_PAID'
        ELSE 'PAID'
    END,
    0
FROM seq;

WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 720
)
INSERT INTO order_details(
    order_id, product_id, quantity,
    sale_unit_price_ex_vat, sale_unit_price_inc_vat, vat_rate, cost_total
)
SELECT
    1 + ((x - 1) / 3),
    p.id,
    1 + (x % 5),
    ROUND(
        ROUND(
            ROUND((1 + (x % 5)) * ROUND(p.sale_price_inc_vat * (0.90 + (x % 3) * 0.05), 2), 2) /
            (1.0 + p.vat_rate / 100.0),
            2
        ) / (1 + (x % 5)),
        12
    ),
    ROUND(p.sale_price_inc_vat * (0.90 + (x % 3) * 0.05), 2),
    p.vat_rate,
    NULL
FROM seq
JOIN products p ON p.id = 1 + ((x * 17) % 120);

UPDATE orders
SET total_inc_vat = ROUND(COALESCE((
        SELECT SUM(ROUND(d.quantity * d.sale_unit_price_inc_vat, 2))
        FROM order_details d
        WHERE d.order_id = orders.id
    ), 0), 2),
    total_ex_vat = ROUND(COALESCE((
        SELECT SUM(ROUND(
            ROUND(d.quantity * d.sale_unit_price_inc_vat, 2) /
            (1.0 + d.vat_rate / 100.0), 2
        ))
        FROM order_details d
        WHERE d.order_id = orders.id
    ), 0), 2);

UPDATE orders
SET paid_amount = CASE payment_status
    WHEN 'PAID' THEN total_inc_vat
    WHEN 'PARTIALLY_PAID' THEN ROUND(total_inc_vat * 0.40, 2)
    ELSE 0
END;

-- Completed demo sales have explicit historical FIFO layers. They are exhausted
-- and separate from current opening stock, so the demo report can show historical
-- cost/profit without altering today's physical quantity.
INSERT INTO inventory_lots(
    id,product_id,supplier_id,received_date,
    initial_quantity,remaining_quantity,unit_cost,source_type
)
SELECT
    -(100000 + d.id),
    d.product_id,
    pref.supplier_id,
    o.order_date,
    d.quantity,
    0,
    p.acquisition_cost,
    'DEMO_SALE_HISTORY'
FROM order_details d
JOIN orders o ON o.id=d.order_id
JOIN products p ON p.id=d.product_id
LEFT JOIN product_suppliers pref
       ON pref.product_id=p.id AND pref.is_preferred=1
WHERE o.status='Completed';

INSERT INTO inventory_lot_consumptions(
    lot_id,order_detail_id,warehouse_movement_id,quantity,unit_cost
)
SELECT
    -(100000 + d.id),
    d.id,
    NULL,
    d.quantity,
    p.acquisition_cost
FROM order_details d
JOIN orders o ON o.id=d.order_id
JOIN products p ON p.id=d.product_id
WHERE o.status='Completed';

UPDATE order_details
SET cost_total = ROUND(quantity * (
    SELECT p.acquisition_cost
    FROM products p
    WHERE p.id=order_details.product_id
), 2)
WHERE order_id IN (
    SELECT id FROM orders WHERE status='Completed'
);

-- Active reservations for open orders.
INSERT INTO stock_reservations(
    product_id, document_type, document_id, reserved_quantity,
    reservation_date, status, notes
)
SELECT
    d.product_id, 'ORDER', o.id, d.quantity,
    o.order_date, 'ACTIVE', 'Reservation generated by test population script'
FROM orders o
JOIN order_details d ON d.order_id = o.id
WHERE o.status IN ('New', 'In Progress');

-- Historical reservations for terminal orders.
INSERT INTO stock_reservations(
    product_id, document_type, document_id, reserved_quantity,
    reservation_date, status, notes
)
SELECT
    d.product_id, 'ORDER', o.id, d.quantity,
    o.order_date,
    CASE WHEN o.status = 'Completed' THEN 'COMPLETED' ELSE 'CANCELLED' END,
    'Historical reservation generated by test population script'
FROM orders o
JOIN order_details d ON d.order_id = o.id
WHERE o.status IN ('Completed', 'Cancelled');

-- Force exact reservation counters even when stock-reservation triggers are installed.
UPDATE products
SET reserved_quantity = COALESCE((
    SELECT SUM(r.reserved_quantity)
    FROM stock_reservations r
    WHERE r.product_id = products.id AND r.status = 'ACTIVE'
), 0);

-- ---------------------------------------------------------------------------
-- SUPPLIER ORDERS: 90 + 270 lines
-- Supported statuses: Draft / Confirmed / In Transit / Completed / Cancelled
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 90
)
INSERT INTO supplier_orders(
    supplier_id, number, order_date, expected_delivery_date,
    status, total, notes
)
SELECT
    1 + ((x - 1) % 18),
    'PO-' || strftime('%Y','now') || '-' || printf('%04d', x),
    datetime('now', '-' || (x % 150) || ' days'),
    datetime('now', '-' || (x % 150) || ' days', '+' || (5 + (x % 25)) || ' days'),
    CASE x % 5
        WHEN 0 THEN 'Draft'
        WHEN 1 THEN 'Confirmed'
        WHEN 2 THEN 'In Transit'
        WHEN 3 THEN 'Completed'
        ELSE 'Cancelled'
    END,
    0,
    CASE x % 3
        WHEN 0 THEN 'Normal replenishment order'
        WHEN 1 THEN 'Urgent stock replenishment'
        ELSE 'Price and delivery test order'
    END
FROM seq;

WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 270
)
INSERT INTO supplier_order_details(
    order_id, product_id, quantity, unit_price, total, notes
)
SELECT
    o.id,
    p.id,
    5 + (x % 26),
    ROUND(ps.purchase_price * CASE WHEN x % 5 = 0 THEN 0.90 ELSE 1.00 END, 2),
    ROUND((5 + (x % 26)) *
        ROUND(ps.purchase_price * CASE WHEN x % 5 = 0 THEN 0.90 ELSE 1.00 END, 2), 2),
    CASE WHEN x % 5 = 0
         THEN 'Negotiated discount below current supplier price'
         ELSE 'Supplier order line generated for testing' END
FROM seq
JOIN supplier_orders o ON o.id = 1 + ((x - 1) / 3)
JOIN products p ON p.id = o.supplier_id + 18 * ((o.id + ((x - 1) % 3)) % 6)
JOIN product_suppliers ps
  ON ps.product_id=p.id AND ps.supplier_id=o.supplier_id;

UPDATE supplier_orders
SET total = ROUND(COALESCE((
    SELECT SUM(d.total)
    FROM supplier_order_details d
    WHERE d.order_id = supplier_orders.id
), 0), 2);

-- Completed supplier orders are historical receipts. They are represented as
-- exhausted lots because the demo's current stock was created separately above.
INSERT INTO inventory_lots(
    product_id,supplier_id,supplier_order_detail_id,received_date,
    initial_quantity,remaining_quantity,unit_cost,source_type
)
SELECT
    d.product_id,o.supplier_id,d.id,o.order_date,
    d.quantity,0,d.unit_price,'LEGACY_SUPPLIER_ORDER'
FROM supplier_order_details d
JOIN supplier_orders o ON o.id=d.order_id
WHERE o.status='Completed';

-- ---------------------------------------------------------------------------
-- INVOICES: 180 + 540 lines
-- Includes currency and immutable snapshots introduced by migration 008.
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 180
)
INSERT INTO invoices(
    number, date, customer_id, taxable_amount, vat, total, status, order_id,
    currency_code, customer_snapshot, company_snapshot, logo_snapshot,
    snapshot_version
)
SELECT
    strftime('%Y','now') || '/' || printf('%04d', x),
    datetime('now', '-' || (x % 240) || ' days'),
    c.id,
    0, 0, 0,
    CASE x % 4
        WHEN 0 THEN 'Draft'
        WHEN 1 THEN 'Issued'
        WHEN 2 THEN 'Paid'
        ELSE 'Canceled'
    END,
    CASE WHEN x <= 90 THEN x ELSE NULL END,
    (SELECT value FROM application_metadata WHERE key = 'currency_code'),
    'first_name=' || c.first_name || char(10) ||
    'last_name=' || c.last_name || char(10) ||
    'email=' || COALESCE(c.email,'') || char(10) ||
    'phone=' || COALESCE(c.phone,'') || char(10) ||
    'address=' || COALESCE(c.address,'') || char(10),
    'company_name=WareStat Demo Company' || char(10) ||
    'vat_number=IT12345678901' || char(10) ||
    'tax_code=DMOCMP86A01H501Z' || char(10) ||
    'address=Via del Magazzino 24' || char(10) ||
    'city=Palermo' || char(10) ||
    'postal_code=90100' || char(10) ||
    'country=Italy' || char(10) ||
    'phone=+39 091 555 0100' || char(10) ||
    'email=amministrazione@demo.warestat.test' || char(10) ||
    'website=https://www.warestat.com' || char(10),
    NULL,
    1
FROM seq
JOIN customers c ON c.id = 1 + ((x * 11) % 120);

WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 540
)
INSERT INTO invoice_details(
    invoice_id, product_id, quantity, sale_unit_price_ex_vat,
    sale_unit_price_inc_vat, vat_rate, total, product_code, product_name
)
SELECT
    1 + ((x - 1) / 3),
    p.id,
    1 + (x % 4),
    ROUND(
        ROUND(
            ROUND((1 + (x % 4)) * ROUND(p.sale_price_inc_vat * (0.92 + (x % 3) * 0.04), 2), 2) /
            (1.0 + p.vat_rate / 100.0),
            2
        ) / (1 + (x % 4)),
        12
    ),
    ROUND(p.sale_price_inc_vat * (0.92 + (x % 3) * 0.04), 2),
    p.vat_rate,
    ROUND(
        ROUND((1 + (x % 4)) *
            ROUND(p.sale_price_inc_vat * (0.92 + (x % 3) * 0.04), 2), 2) /
        (1.0 + p.vat_rate / 100.0),
        2
    ),
    p.code,
    p.name
FROM seq
JOIN products p ON p.id = 1 + ((x * 19) % 120);

-- Final reconciliation from the values actually stored in SQLite.
-- Reconcile net line totals from the exact VAT-inclusive customer price.
UPDATE invoice_details
SET total = ROUND(
    ROUND(quantity * sale_unit_price_inc_vat, 2) /
    (1.0 + vat_rate / 100.0),
    2
);

UPDATE invoices
SET taxable_amount = ROUND(COALESCE((
        SELECT SUM(d.total)
        FROM invoice_details d
        WHERE d.invoice_id = invoices.id
    ), 0), 2),
    vat = ROUND(COALESCE((
        SELECT SUM(ROUND(d.quantity * d.sale_unit_price_inc_vat, 2) - d.total)
        FROM invoice_details d
        WHERE d.invoice_id = invoices.id
    ), 0), 2);

UPDATE invoices
SET total = ROUND(taxable_amount + vat, 2);

INSERT INTO invoice_numbering(year, last_number)
VALUES (CAST(strftime('%Y','now') AS INTEGER), 180);

-- ---------------------------------------------------------------------------
-- WAREHOUSE MOVEMENTS: 600
-- Uses the current INWARD / OUTWARD vocabulary.
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 600
)
INSERT INTO warehouse_movements(
    product_id, date, type, quantity, reason,
    document_number, document_type, notes
)
SELECT
    1 + ((x * 23) % 120),
    datetime('now', '-' || (x % 210) || ' days', '-' || (x % 20) || ' hours'),
    CASE WHEN x % 3 = 0 THEN 'OUTWARD' ELSE 'INWARD' END,
    1 + (x % 30),
    CASE x % 6
        WHEN 0 THEN 'Customer order fulfillment'
        WHEN 1 THEN 'Supplier delivery'
        WHEN 2 THEN 'Inventory adjustment'
        WHEN 3 THEN 'Customer return'
        WHEN 4 THEN 'Damaged stock correction'
        ELSE 'Manual warehouse movement'
    END,
    'DOC-' || printf('%06d', x),
    CASE x % 4
        WHEN 0 THEN 'ORDER'
        WHEN 1 THEN 'SUPPLIER_ORDER'
        WHEN 2 THEN 'INVENTORY_ADJUSTMENT'
        ELSE 'INTERNAL'
    END,
    'Historical movement generated for WareStat testing'
FROM seq;

-- ---------------------------------------------------------------------------
-- WAREHOUSE NOTIFICATIONS: 60
-- ---------------------------------------------------------------------------
WITH RECURSIVE seq(x) AS (
    SELECT 1
    UNION ALL
    SELECT x + 1 FROM seq WHERE x < 60
)
INSERT INTO warehouse_notifications(product_id, date, type, message, status)
SELECT
    1 + ((x * 7) % 120),
    datetime('now', '-' || (x % 30) || ' days'),
    CASE x % 3
        WHEN 0 THEN 'LOW_STOCK'
        WHEN 1 THEN 'OUT_OF_STOCK'
        ELSE 'REORDER_NEEDED'
    END,
    CASE x % 3
        WHEN 0 THEN 'Stock is below the configured minimum level.'
        WHEN 1 THEN 'Product is out of stock and requires attention.'
        ELSE 'Reorder settings indicate that replenishment should be evaluated.'
    END,
    CASE x % 3
        WHEN 0 THEN 'NEW'
        WHEN 1 THEN 'READ'
        ELSE 'HANDLED'
    END
FROM seq;

COMMIT;

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------------
-- VALIDATION / SUMMARY
-- ---------------------------------------------------------------------------
PRAGMA foreign_key_check;
PRAGMA integrity_check;

SELECT '=== WARESTAT TEST DATABASE READY ===' AS result
UNION ALL SELECT 'Currency: ' || value FROM application_metadata WHERE key='currency_code'
UNION ALL SELECT 'Customers: ' || COUNT(*) FROM customers
UNION ALL SELECT 'Suppliers: ' || COUNT(*) FROM suppliers
UNION ALL SELECT 'Products: ' || COUNT(*) FROM products
UNION ALL SELECT '  Out of stock: ' || COUNT(*) FROM products WHERE quantity = 0
UNION ALL SELECT '  Negative stock: ' || COUNT(*) FROM products WHERE quantity < 0
UNION ALL SELECT '  In stock with zero acquisition cost: ' || COUNT(*) FROM products WHERE quantity > 0 AND acquisition_cost = 0
UNION ALL SELECT '  Inactive: ' || COUNT(*) FROM products WHERE active = 0
UNION ALL SELECT 'Customer orders: ' || COUNT(*) FROM orders
UNION ALL SELECT '  Active reservations: ' || COUNT(*) FROM stock_reservations WHERE status='ACTIVE'
UNION ALL SELECT 'Invoices: ' || COUNT(*) FROM invoices
UNION ALL SELECT '  Invoice line mismatches: ' || COUNT(*)
    FROM invoice_details
    WHERE ABS(total - ROUND(ROUND(quantity * sale_unit_price_inc_vat, 2) / (1.0 + vat_rate / 100.0), 2)) > 0.004
UNION ALL SELECT '  Invoice header mismatches: ' || COUNT(*)
    FROM invoices i
    WHERE ABS(i.taxable_amount - COALESCE((
              SELECT ROUND(SUM(d.total), 2)
              FROM invoice_details d WHERE d.invoice_id=i.id
          ), 0)) > 0.004
       OR ABS(i.vat - COALESCE((
              SELECT ROUND(SUM(ROUND(d.quantity * d.sale_unit_price_inc_vat, 2) - d.total), 2)
              FROM invoice_details d WHERE d.invoice_id=i.id
          ), 0)) > 0.004
       OR ABS(i.total - ROUND(i.taxable_amount + i.vat, 2)) > 0.004
UNION ALL SELECT 'Supplier orders: ' || COUNT(*) FROM supplier_orders
UNION ALL SELECT 'Product/supplier relations: ' || COUNT(*) FROM product_suppliers
UNION ALL SELECT 'FIFO lots: ' || COUNT(*) FROM inventory_lots
UNION ALL SELECT 'Sales with historical cost: ' || COUNT(*) FROM order_details WHERE cost_total IS NOT NULL
UNION ALL SELECT 'Warehouse movements: ' || COUNT(*) FROM warehouse_movements
UNION ALL SELECT 'Warehouse notifications: ' || COUNT(*) FROM warehouse_notifications
UNION ALL SELECT 'Product images: ' || COUNT(*) || ' (add from UI for photo/backup tests)' FROM product_images;
