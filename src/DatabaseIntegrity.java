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
        Map.entry("invoices", "id number date customer_id taxable_amount vat total status order_id currency_code customer_snapshot company_snapshot logo_snapshot snapshot_version"),
        Map.entry("invoice_details", "id invoice_id product_id quantity unit_price vat_rate total product_code product_name"),
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
            String currency=DatabaseCurrency.read(c);
            try {DatabaseCurrency.codeFromSetting(currency);} catch(IllegalArgumentException e) {throw new SQLException("Database currency is missing or invalid",e);}
            for(var rule:rules().entrySet()) {
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT rowid FROM "+rule.getKey()+" WHERE "+rule.getValue().replace("NEW.","")+" LIMIT 1")) {
                    if(r.next())throw new SQLException("Invalid existing numeric data in "+rule.getKey()+", row "+r.getLong(1)+". Reconcile the source database before import or release.");
                }
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

    private static String amount(String column) {
        return "typeof(NEW."+column+") NOT IN ('integer','real') OR NEW."+column+" NOT BETWEEN 0 AND 999999999999.99";
    }
    private static String integer(String column,int min) {
        return "typeof(NEW."+column+")<>'integer' OR NEW."+column+" NOT BETWEEN "+min+" AND 2147483647";
    }
    private static Map<String,String> rules() {
        Map<String,String> rules=new LinkedHashMap<>();
        rules.put("products",integer("quantity",Integer.MIN_VALUE)+" OR "+integer("reserved_quantity",0)+" OR "+integer("minimum_quantity",0)+" OR "+amount("price")+" OR "+amount("acquisition_cost")+" OR "+amount("vat_rate")+" OR NEW.vat_rate>100 OR "+amount("weight"));
        String line=integer("quantity",1)+" OR "+amount("unit_price");
        rules.put("order_details",line);
        rules.put("invoice_details",line+" OR "+amount("vat_rate")+" OR NEW.vat_rate>100 OR "+amount("total"));
        rules.put("supplier_order_details",line+" OR "+amount("total"));
        rules.put("orders",amount("total")+" OR "+amount("paid_amount"));
        rules.put("invoices",amount("taxable_amount")+" OR "+amount("vat")+" OR "+amount("total"));
        rules.put("supplier_orders",amount("total"));
        rules.put("supplier_price_lists",amount("price")+" OR "+integer("minimum_quantity",1));
        rules.put("warehouse_movements",integer("quantity",1));
        rules.put("stock_reservations",integer("reserved_quantity",1));
        rules.put("minimum_stock",integer("minimum_quantity",0)+" OR "+integer("reorder_quantity",0)+" OR (NEW.lead_time_days IS NOT NULL AND ("+integer("lead_time_days",0)+"))");
        return rules;
    }
    public static void installGuards(Connection c) throws SQLException {
        for(var rule:rules().entrySet())guard(c,rule.getKey(),rule.getValue());
    }

    private static void guard(Connection c, String table, String condition) throws SQLException {
        for (String action : List.of("INSERT", "UPDATE")) {
            try (Statement s = c.createStatement()) {
                s.execute("DROP TRIGGER IF EXISTS numeric_guard_"+table+"_"+action);
                s.execute("CREATE TRIGGER numeric_guard_" + table + "_" + action + " BEFORE " + action + " ON " + table +
                    " WHEN " + condition + " BEGIN SELECT RAISE(ABORT, 'Invalid numeric value in " + table + "'); END");
            }
        }
    }
}
