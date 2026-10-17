import java.sql.*;
import java.util.*;

/** Checks application structure as well as SQLite page and reference integrity. */
public final class DatabaseIntegrity {
    private DatabaseIntegrity() {}

    /** Minimal shape accepted before migrations when validating an older backup. */
    private static final Map<String, String> LEGACY_CORE = Map.of(
        "products", "id code name quantity",
        "customers", "id first_name last_name email phone address",
        "orders", "id customer_id order_date status",
        "order_details", "id order_id product_id quantity");

    private static final Map<String, String> CURRENT = Map.ofEntries(
        Map.entry("products", "id code name description sale_price_ex_vat sale_price_inc_vat quantity reserved_quantity category alternative_sku weight unit_of_measure acquisition_cost active supplier_id warehouse_position vat_rate"),
        Map.entry("product_images", "id product_id filename is_primary sort_order"),
        Map.entry("suppliers", "id company_name vat_number tax_code address phone email certified_email website notes"),
        Map.entry("orders", "id customer_id order_date completed_date status total_ex_vat total_inc_vat payment_status paid_amount"),
        Map.entry("order_details", "id order_id product_id quantity sale_unit_price_ex_vat sale_unit_price_inc_vat vat_rate cost_total"),
        Map.entry("invoices", "id number date customer_id taxable_amount vat total status payment_status paid_amount order_id currency_code customer_snapshot company_snapshot logo_snapshot snapshot_version"),
        Map.entry("invoice_details", "id invoice_id product_id quantity sale_unit_price_ex_vat sale_unit_price_inc_vat vat_rate total product_code product_name"),
        Map.entry("supplier_orders", "id supplier_id number order_date expected_delivery_date received_date status total notes"),
        Map.entry("supplier_order_details", "id order_id product_id quantity unit_price total notes"),
        Map.entry("supplier_returns", "id order_id returned_at reversed_at notes"),
        Map.entry("supplier_return_items", "id return_id supplier_order_detail_id lot_id product_id quantity unit_cost unit_price warehouse_movement_id"),
        Map.entry("product_suppliers", "id product_id supplier_id supplier_product_code purchase_price is_preferred notes"),
        Map.entry("inventory_lots", "id product_id supplier_id supplier_order_detail_id warehouse_movement_id received_date initial_quantity remaining_quantity unit_cost source_type"),
        Map.entry("inventory_lot_consumptions", "id lot_id order_detail_id warehouse_movement_id quantity unit_cost"),
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
        requireColumns(c, legacy ? LEGACY_CORE : CURRENT);
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
            if (r.next()) throw new SQLException("Orphaned database reference in " + r.getString(1));
        }
        if (!legacy) {
            String currency=DatabaseCurrency.read(c);
            try {DatabaseCurrency.codeFromSetting(currency);} catch(IllegalArgumentException e) {throw new SQLException("Database currency is missing or invalid",e);}
            for(var rule:rules(c).entrySet()) {
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT rowid FROM "+rule.getKey()+" WHERE "+rule.getValue().replace("NEW.","")+" LIMIT 1")) {
                    if(r.next())throw new SQLException("Invalid existing numeric data in "+rule.getKey()+", row "+r.getLong(1)+". Reconcile the source database before import or release.");
                }
            }
            validateSupplierReturns(c);
        }
    }

    /**
     * Read-only checks for purchase-linked returns. A compensating return movement
     * restores its exact FIFO lot rather than deleting the original consumption;
     * therefore a reversed return must retain both its outward and inward history.
     */
    private static void validateSupplierReturns(Connection c) throws SQLException {
        rejectInvalidReturn(c, """
            SELECT sr.id FROM supplier_returns sr
            WHERE NOT EXISTS (
                SELECT 1 FROM supplier_return_items ri WHERE ri.return_id=sr.id
            ) LIMIT 1
            """, "Supplier return has no items");

        rejectInvalidReturn(c, """
            SELECT ri.id FROM supplier_return_items ri
            WHERE typeof(ri.quantity)<>'integer'
               OR ri.quantity NOT BETWEEN 1 AND 2147483647
               OR typeof(ri.unit_cost) NOT IN ('integer','real')
               OR ri.unit_cost NOT BETWEEN 0 AND 999999999999.99
               OR typeof(ri.unit_price) NOT IN ('integer','real')
               OR ri.unit_price NOT BETWEEN 0 AND 999999999999.99
            LIMIT 1
            """, "Supplier return has invalid quantity or cost");

        // Foreign-key existence is covered by PRAGMA foreign_key_check above.
        // These checks additionally require the referenced entities to match.
        rejectInvalidReturn(c, """
            SELECT ri.id FROM supplier_return_items ri
            JOIN supplier_returns sr ON sr.id=ri.return_id
            JOIN supplier_orders o ON o.id=sr.order_id
            JOIN supplier_order_details d ON d.id=ri.supplier_order_detail_id
            JOIN inventory_lots l ON l.id=ri.lot_id
            WHERE d.order_id IS NOT sr.order_id
               OR d.product_id IS NOT ri.product_id
               OR l.product_id IS NOT ri.product_id
               OR l.supplier_order_detail_id IS NOT ri.supplier_order_detail_id
               OR l.supplier_id IS NOT o.supplier_id
               OR l.source_type<>'SUPPLIER_ORDER'
               OR l.initial_quantity IS NOT d.quantity
               OR l.unit_cost IS NOT ri.unit_cost
               OR d.unit_price IS NOT ri.unit_price
            LIMIT 1
            """, "Supplier return references the wrong purchase or FIFO lot");

        // Storned returns no longer count against the purchased quantity.
        rejectInvalidReturn(c, """
            SELECT d.id FROM supplier_order_details d
            JOIN supplier_return_items ri ON ri.supplier_order_detail_id=d.id
            JOIN supplier_returns sr ON sr.id=ri.return_id
            WHERE sr.reversed_at IS NULL
            GROUP BY d.id, d.quantity
            HAVING SUM(ri.quantity)>d.quantity
            LIMIT 1
            """, "Active supplier returns exceed purchased quantity");

        // Every returned line must have exactly one outward movement and
        // exactly one consumption of the specific original FIFO lot.
        rejectInvalidReturn(c, """
            SELECT ri.id FROM supplier_return_items ri
            JOIN warehouse_movements m ON m.id=ri.warehouse_movement_id
            WHERE m.product_id IS NOT ri.product_id
               OR m.quantity IS NOT ri.quantity
               OR m.type<>'OUTWARD'
               OR m.document_type IS NOT 'SUPPLIER_RETURN'
               OR m.reason<>'SUPPLIER_RETURN'
               OR m.document_number IS NULL
               OR m.document_number<>(SELECT o.number||'/R'||sr.id
                   FROM supplier_returns sr
                   JOIN supplier_orders o ON o.id=sr.order_id
                   WHERE sr.id=ri.return_id)
               OR (SELECT COUNT(*) FROM inventory_lot_consumptions x
                   WHERE x.warehouse_movement_id=ri.warehouse_movement_id)<>1
               OR NOT EXISTS (
                   SELECT 1 FROM inventory_lot_consumptions x
                   WHERE x.warehouse_movement_id=ri.warehouse_movement_id
                     AND x.lot_id=ri.lot_id
                     AND x.order_detail_id IS NULL
                     AND x.quantity=ri.quantity
                     AND x.unit_cost=ri.unit_cost
               )
            LIMIT 1
            """, "Supplier return warehouse movement or FIFO consumption is inconsistent");

        // Each active supplier return must remain deducted from its original FIFO lot.
        // Other sales and movements can reduce that lot further, so this is a
        // conservative bound, not an unsafe attempt to reconstruct old opening stock.
        rejectInvalidReturn(c, """
            SELECT l.id FROM inventory_lots l
            WHERE EXISTS (SELECT 1 FROM supplier_return_items ri WHERE ri.lot_id=l.id)
              AND l.initial_quantity-l.remaining_quantity <
                  COALESCE((SELECT SUM(ri.quantity)
                    FROM supplier_return_items ri
                    JOIN supplier_returns sr ON sr.id=ri.return_id
                    WHERE ri.lot_id=l.id AND sr.reversed_at IS NULL),0)
            LIMIT 1
            """, "Active supplier return quantity was not deducted from FIFO lot");

        // Each reversal must restore the same product/quantity exactly once;
        // an active return must not have a compensating movement.
        rejectInvalidReturn(c, """
            SELECT ri.id FROM supplier_return_items ri
            JOIN supplier_returns sr ON sr.id=ri.return_id
            WHERE (SELECT COUNT(*) FROM warehouse_movements rev
                   WHERE rev.reversal_of_movement_id=ri.warehouse_movement_id)
                    <> CASE WHEN sr.reversed_at IS NULL THEN 0 ELSE 1 END
               OR (sr.reversed_at IS NOT NULL AND NOT EXISTS (
                   SELECT 1 FROM warehouse_movements rev
                   WHERE rev.reversal_of_movement_id=ri.warehouse_movement_id
                     AND rev.product_id=ri.product_id
                     AND rev.quantity=ri.quantity
                     AND rev.type='INWARD'
                     AND rev.document_type='SUPPLIER_RETURN_REVERSAL'
                     AND rev.reason='SUPPLIER_RETURN_REVERSAL'
                     AND rev.document_number='R'||sr.id
               ))
            LIMIT 1
            """, "Supplier return reversal is missing, duplicated or inconsistent");

        rejectInvalidReturn(c, """
            SELECT rev.id FROM warehouse_movements rev
            WHERE rev.document_type='SUPPLIER_RETURN_REVERSAL'
              AND NOT EXISTS (
                  SELECT 1 FROM supplier_return_items ri
                  JOIN supplier_returns sr ON sr.id=ri.return_id
                  WHERE ri.warehouse_movement_id=rev.reversal_of_movement_id
                    AND sr.reversed_at IS NOT NULL
                    AND rev.product_id=ri.product_id
                    AND rev.quantity=ri.quantity
                    AND rev.type='INWARD'
                    AND rev.reason='SUPPLIER_RETURN_REVERSAL'
              )
            LIMIT 1
            """, "Supplier return reversal has no valid original return");
    }

    private static void rejectInvalidReturn(Connection c, String query, String message)
            throws SQLException {
        try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(query)) {
            if (r.next())
                throw new SQLException(message+" (record "+r.getLong(1)+"). Reconcile the database before import or release.");
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
    private static String nullableAmount(String column) {
        return "NEW."+column+" IS NOT NULL AND ("+amount(column)+")";
    }

    private static Map<String,String> rules(Connection c) throws SQLException {
        Map<String,String> rules=new LinkedHashMap<>();

        String productPrice = hasColumn(c,"products","sale_price_ex_vat")
            ? "sale_price_ex_vat" : "price";
        String productRule = integer("quantity",Integer.MIN_VALUE)
            +" OR "+integer("reserved_quantity",0)
            +" OR "+amount(productPrice)
            +" OR "+amount("acquisition_cost")
            +" OR "+amount("vat_rate")+" OR NEW.vat_rate>100 OR "+amount("weight");
        if (hasColumn(c,"products","sale_price_inc_vat"))
            productRule += " OR " + amount("sale_price_inc_vat");
        rules.put("products",productRule);

        String orderUnitPrice = hasColumn(c,"order_details","sale_unit_price_ex_vat")
            ? "sale_unit_price_ex_vat" : "unit_price";
        String orderLine = integer("quantity",1)+" OR "+amount(orderUnitPrice);
        if (hasColumn(c,"order_details","sale_unit_price_inc_vat"))
            orderLine += " OR " + amount("sale_unit_price_inc_vat");
        if (hasColumn(c,"order_details","vat_rate"))
            orderLine += " OR " + amount("vat_rate") + " OR NEW.vat_rate>100";
        if (hasColumn(c,"order_details","cost_total"))
            orderLine += " OR ("+nullableAmount("cost_total")+")";
        rules.put("order_details",orderLine);

        String invoiceUnitPrice = hasColumn(c,"invoice_details","sale_unit_price_ex_vat")
            ? "sale_unit_price_ex_vat" : "unit_price";
        String invoiceLine = integer("quantity",1)+" OR "+amount(invoiceUnitPrice);
        if (hasColumn(c,"invoice_details","sale_unit_price_inc_vat"))
            invoiceLine += " OR " + amount("sale_unit_price_inc_vat");
        invoiceLine += " OR "+amount("vat_rate")+" OR NEW.vat_rate>100 OR "+amount("total");
        rules.put("invoice_details",invoiceLine);

        String supplierLine=integer("quantity",1)+" OR "+amount("unit_price");
        rules.put("supplier_order_details",supplierLine+" OR "+amount("total"));

        String orderTotal = hasColumn(c,"orders","total_ex_vat") ? "total_ex_vat" : "total";
        String orderRule = amount(orderTotal)+" OR "+amount("paid_amount");
        if (hasColumn(c,"orders","total_inc_vat")) orderRule += " OR " + amount("total_inc_vat");
        rules.put("orders",orderRule);

        String invoiceRule=amount("taxable_amount")+" OR "+amount("vat")+" OR "+amount("total");
        if (hasColumn(c, "invoices", "paid_amount")) invoiceRule += " OR " + amount("paid_amount");
        rules.put("invoices", invoiceRule);
        rules.put("supplier_orders",amount("total"));
        rules.put("product_suppliers",amount("purchase_price")+" OR "+integer("is_preferred",0)+" OR NEW.is_preferred>1");
        rules.put("inventory_lots",integer("initial_quantity",0)+" OR "+integer("remaining_quantity",0)+" OR NEW.remaining_quantity>NEW.initial_quantity OR "+amount("unit_cost"));
        rules.put("inventory_lot_consumptions",integer("quantity",1)+" OR "+amount("unit_cost"));
        rules.put("warehouse_movements",integer("quantity",1));
        rules.put("stock_reservations",integer("reserved_quantity",1));
        rules.put("minimum_stock",integer("minimum_quantity",0)+" OR "+integer("reorder_quantity",0)+" OR (NEW.lead_time_days IS NOT NULL AND ("+integer("lead_time_days",0)+"))");
        return rules;
    }

    public static void installGuards(Connection c) throws SQLException {
        for(var rule:rules(c).entrySet())guard(c,rule.getKey(),rule.getValue());
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (r.next()) if (column.equals(r.getString("name"))) return true;
            return false;
        }
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
