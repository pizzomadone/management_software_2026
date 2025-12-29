-- Script to populate the management software database with dummy but valid data
-- This version DROPS and recreates tables to ensure clean data
PRAGMA foreign_keys = OFF;
PRAGMA synchronous = OFF;
PRAGMA journal_mode = MEMORY;

-- Drop all tables to ensure clean slate
DROP TABLE IF EXISTS warehouse_notifications;
DROP TABLE IF EXISTS warehouse_movements;
DROP TABLE IF EXISTS minimum_stock;
DROP TABLE IF EXISTS supplier_price_lists;
DROP TABLE IF EXISTS supplier_order_details;
DROP TABLE IF EXISTS supplier_orders;
DROP TABLE IF EXISTS invoice_details;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS invoice_numbering;
DROP TABLE IF EXISTS order_details;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS stock_reservations;
DROP TABLE IF EXISTS company_data;

-- Note: Now the application will recreate the tables on next startup
-- Run the application once to let it create the tables, then run this script

SELECT 'All tables dropped. Close this window, restart the application to recreate tables, then run script_populate.sql' as message;
