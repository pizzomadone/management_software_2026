import java.sql.*;
import java.util.*;

/** Checks application structure as well as SQLite page and reference integrity. */
public final class DatabaseIntegrity {
    private DatabaseIntegrity() {}

    private static final Map<String, String> CORE = Map.of(
        "products", "id code name price quantity",
        "customers", "id first_name last_name email phone address",
        "orders", "id customer_id order_date status total",
        "order_details", "id order_id product_id quantity unit_price");
    private static final Map<String, String> CURRENT = Map.ofEntries(
        Map.entry("products", "description reserved_quantity category alternative_sku weight unit_of_measure minimum_quantity acquisition_cost active supplier_id warehouse_position vat_rate"),
        Map.entry("suppliers", "id company_name vat_number tax_code address phone email certified_email website notes"),
        Map.entry("orders", "payment_status paid_amount"),
        Map.entry("invoices", "id number date customer_id taxable_amount vat total status order_id"),
        Map.entry("invoice_details", "id invoice_id product_id quantity unit_price vat_rate total"),
        Map.entry("supplier_orders", "id supplier_id number order_date expected_delivery_date status total notes"),
        Map.entry("supplier_order_details", "id order_id product_id quantity unit_price total notes"),
        Map.entry("supplier_price_lists", "id supplier_id product_id supplier_product_code price minimum_quantity validity_start_date validity_end_date notes"),
        Map.entry("warehouse_movements", "id product_id date type quantity reason document_number document_type notes"),
        Map.entry("stock_reservations", "id product_id document_type document_id reserved_quantity status"),
        Map.entry("minimum_stock", "product_id minimum_quantity reorder_quantity lead_time_days preferred_supplier_id notes"),
        Map.entry("warehouse_notifications", "id product_id date type message status"),
        Map.entry("company_data", "id company_name vat_number tax_code address city postal_code country phone email website logo_path"),
        Map.entry("invoice_numbering", "year last_number"));

    public static void validate(Connection c, boolean legacy) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
            if (!r.next() || !"ok".equalsIgnoreCase(r.getString(1)))
                throw new SQLException("SQLite integrity check failed");
        }
        requireColumns(c, CORE);
        if (!legacy) requireColumns(c, CURRENT);
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
            if (r.next()) throw new SQLException("Orphaned database reference in " + r.getString(1));
        }
        if (!legacy) {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "SELECT id FROM products WHERE typeof(quantity)<>'integer' OR quantity NOT BETWEEN -2147483648 AND 2147483647 " +
                    "OR typeof(reserved_quantity)<>'integer' OR reserved_quantity NOT BETWEEN 0 AND 2147483647 LIMIT 1")) {
                if (r.next()) throw new SQLException("Unsupported stock quantity for product " + r.getInt(1));
            }
        }
    }

    private static void requireColumns(Connection c, Map<String,String> tables) throws SQLException {
        for (var table : tables.entrySet()) {
            Set<String> columns = new HashSet<>();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(" + table.getKey() + ")")) {
                while (r.next()) columns.add(r.getString("name"));
            }
            for (String col : table.getValue().split(" ")) if (!columns.contains(col))
                throw new SQLException("Missing required WareStat field: " + table.getKey() + "." + col);
        }
    }

    public static void installGuards(Connection c) throws SQLException {
        guard(c, "products", "typeof(NEW.quantity)<>'integer' OR NEW.quantity NOT BETWEEN -2147483648 AND 2147483647 OR " +
            "typeof(NEW.reserved_quantity)<>'integer' OR NEW.reserved_quantity NOT BETWEEN 0 AND 2147483647 OR " +
            "NEW.price IS NULL OR NEW.price NOT BETWEEN 0 AND 999999999999.99 OR " +
            "NEW.acquisition_cost IS NULL OR NEW.acquisition_cost NOT BETWEEN 0 AND 999999999999.99 OR " +
            "NEW.vat_rate IS NULL OR NEW.vat_rate NOT BETWEEN 0 AND 100 OR NEW.weight IS NULL OR NEW.weight<0");
        for (String table : List.of("order_details", "invoice_details", "supplier_order_details"))
            guard(c, table, "typeof(NEW.quantity)<>'integer' OR NEW.quantity NOT BETWEEN 1 AND 2147483647 OR " +
                "NEW.unit_price IS NULL OR NEW.unit_price NOT BETWEEN 0 AND 999999999999.99");
        guard(c, "orders", "NEW.total IS NULL OR NEW.total NOT BETWEEN 0 AND 999999999999.99 OR " +
            "NEW.paid_amount IS NULL OR NEW.paid_amount NOT BETWEEN 0 AND 999999999999.99");
        guard(c, "warehouse_movements", "typeof(NEW.quantity)<>'integer' OR NEW.quantity NOT BETWEEN 1 AND 2147483647");
        guard(c, "stock_reservations", "typeof(NEW.reserved_quantity)<>'integer' OR NEW.reserved_quantity NOT BETWEEN 1 AND 2147483647");
    }

    private static void guard(Connection c, String table, String condition) throws SQLException {
        for (String action : List.of("INSERT", "UPDATE")) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TRIGGER IF NOT EXISTS numeric_guard_" + table + "_" + action + " BEFORE " + action + " ON " + table +
                    " WHEN " + condition + " BEGIN SELECT RAISE(ABORT, 'Invalid numeric value in " + table + "'); END");
            }
        }
    }
}
