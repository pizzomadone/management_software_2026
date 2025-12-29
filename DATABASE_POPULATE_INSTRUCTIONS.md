# Database Population Instructions

## Problem
The `DELETE` statements in `script_populate.sql` may not work properly if your SQL execution tool (DB Browser for SQLite, etc.) runs each statement in a separate connection, preventing the `PRAGMA foreign_keys = OFF` from taking effect.

## Solution Options

### Option 1: Use script_populate.sql (Recommended if DELETE works)

1. **Important**: Before running the script, ensure foreign keys are disabled:
   - In **DB Browser for SQLite**: Go to Edit → Preferences → SQL tab → Uncheck "Foreign Keys"
   - In **SQLite CLI**: Run `PRAGMA foreign_keys = OFF;` before executing the script

2. Run `script_populate.sql`

3. Check the output statistics to verify all records were created:
   - Customers: 1000
   - Suppliers: 50
   - Products: 500
   - Orders: 2000
   - Invoices: 1500
   - Etc.

### Option 2: Use DROP TABLE approach (Most reliable)

If DELETE doesn't work (old data remains), use this two-step process:

1. **Close the application** completely

2. Run `script_populate_clean.sql` to drop all tables

3. **Restart the application** - it will automatically recreate all tables with the correct schema

4. **Close the application** again

5. Run `script_populate.sql` to insert the dummy data

6. **Restart the application** to use it with the populated data

### Option 3: Manual cleanup via SQL

If you prefer manual control, execute these commands in order:

```sql
PRAGMA foreign_keys = OFF;

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
```

Then run `script_populate.sql` with the DELETE statements commented out.

## Verification

After running the population script, verify the data by checking:

1. Customer count: Should see 1000 customers with names like "John Smith", "Mary Johnson", etc.
2. Old data should be gone (no entries like "carmelo m ()" which was test data)
3. Dashboard should show proper KPI values (warehouse value, pending orders, etc.)

## Troubleshooting

**If you see old data after running DELETE:**
- Your SQL tool is likely running statements in separate connections
- Use Option 2 (DROP TABLE approach) instead

**If you see fewer records than expected:**
- Check your SQLite version: `SELECT sqlite_version();`
- Requires SQLite 3.8.3+ for WITH RECURSIVE
- Requires SQLite 3.25.0+ if using window functions (script has been updated to avoid these)

**If you get "no such table" errors:**
- Make sure the application has run at least once to create the schema
- Or use Option 2 to have the app recreate tables
