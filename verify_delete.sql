-- Verify that DELETE works and show current database state
PRAGMA foreign_keys = OFF;

-- Show current counts BEFORE delete
SELECT '=== BEFORE DELETE ===' as step
UNION ALL SELECT 'Customers: ' || COUNT(*) FROM customers
UNION ALL SELECT 'Suppliers: ' || COUNT(*) FROM suppliers
UNION ALL SELECT 'Products: ' || COUNT(*) FROM products
UNION ALL SELECT 'Orders: ' || COUNT(*) FROM orders
UNION ALL SELECT 'Invoices: ' || COUNT(*) FROM invoices
UNION ALL SELECT '';

-- Perform deletes
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

-- Show current counts AFTER delete
SELECT '=== AFTER DELETE ===' as step
UNION ALL SELECT 'Customers: ' || COUNT(*) FROM customers
UNION ALL SELECT 'Suppliers: ' || COUNT(*) FROM suppliers
UNION ALL SELECT 'Products: ' || COUNT(*) FROM products
UNION ALL SELECT 'Orders: ' || COUNT(*) FROM orders
UNION ALL SELECT 'Invoices: ' || COUNT(*) FROM invoices;
