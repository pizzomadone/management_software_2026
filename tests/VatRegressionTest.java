import java.sql.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

/** Exercises the actual price, payment, invoice and backup paths on synthetic data. */
public class VatRegressionTest extends RegressionTest {
    static Order loadOrder(int id) throws Exception {
        return (Order)call(new OrdersPanel(), "loadOrderDetails", new Class<?>[]{int.class}, id);
    }
    static int invoiceOrder(int id) throws Exception {
        Order order=loadOrder(id);
        DatabaseManager.beginTransaction(db);
        try {
            int invoiceId=OrdersPanel.createInvoiceFromOrder(db,order).id();
            db.commit();return invoiceId;
        } catch(Exception e) {db.rollback();throw e;}
        finally {db.setAutoCommit(true);}
    }
    static void exportInvoice(int id,String filename) throws Exception {
        Invoice invoice=new Invoice(id,"TEST",new java.util.Date(),1,"",0,0,0,"Draft");
        Path pdf=OUT.resolve(filename);
        call(new InvoicePDFGenerator(invoice,customer()),"generatePDF",new Class<?>[]{java.io.File.class},pdf.toFile());
        require(!ExtendedRegressionTest.pdfText(pdf).isBlank(),"Empty invoice PDF");
    }
    static void oldSchema() throws Exception {
        for(String table:List.of("products","orders","order_details","invoice_details"))
            for(String action:List.of("INSERT","UPDATE"))
                sql("DROP TRIGGER IF EXISTS numeric_guard_"+table+"_"+action);
        sql("DELETE FROM schema_migrations WHERE version='017'");
        sql("ALTER TABLE products RENAME COLUMN sale_price_ex_vat TO price");
        sql("ALTER TABLE products DROP COLUMN sale_price_inc_vat");
        sql("ALTER TABLE orders RENAME COLUMN total_ex_vat TO total");
        sql("ALTER TABLE orders DROP COLUMN total_inc_vat");
        sql("ALTER TABLE order_details RENAME COLUMN sale_unit_price_ex_vat TO unit_price");
        sql("ALTER TABLE order_details DROP COLUMN sale_unit_price_inc_vat");
        sql("ALTER TABLE order_details DROP COLUMN vat_rate");
        sql("ALTER TABLE invoice_details RENAME COLUMN sale_unit_price_ex_vat TO unit_price");
        sql("ALTER TABLE invoice_details DROP COLUMN sale_unit_price_inc_vat");
    }
    static void vat() throws Exception {
        test("linked invoice date and numbering follow its own document date, not the order",()->{
            fresh();
            java.util.Date oldOrderDate=new java.text.SimpleDateFormat("dd/MM/yyyy").parse("15/01/2024");
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) "
                +"VALUES(1,1,"+oldOrderDate.getTime()+",'Completed',30,30)");
            sql("INSERT INTO order_details(order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate) "
                +"VALUES(1,1,3,10,10,0)");
            Order oldOrder=loadOrder(1);
            DatabaseManager.beginTransaction(db);
            OrdersPanel.GeneratedInvoice generated;
            try {
                generated=OrdersPanel.createInvoiceFromOrder(db,oldOrder);
                db.commit();
            } catch(Exception error) {db.rollback();throw error;}
            finally {db.setAutoCommit(true);}

            int draftYear=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            String today=java.time.LocalDate.now().toString();
            require(value("SELECT warestat_date(date) FROM invoices WHERE id="+generated.id()).equals(today),
                "Generated draft copied the historical order date");
            require(generated.number().equals(InvoiceDialog.draftReference(generated.id())),
                "Order-generated draft received a fiscal invoice number");
            require("0".equals(value("SELECT COUNT(*) FROM invoice_numbering")),
                "Creating an invoice draft consumed a fiscal number");

            // Saving the same linked draft with another date must not issue a number.
            java.util.Date draftEditDate=new java.text.SimpleDateFormat("dd/MM/yyyy")
                .parse("02/05/"+draftYear);
            DatabaseManager.beginTransaction(db);
            try {
                String draftRef=InvoiceDialog.saveLinkedInvoiceStatus(
                    db,generated.id(),"Draft","Draft",draftEditDate);
                require(draftRef.equals(generated.number()),
                    "Editing a linked draft changed its internal reference");
                db.commit();
            } catch(Exception error) {db.rollback();throw error;}
            finally {db.setAutoCommit(true);}
            require((draftYear+"-05-02").equals(value(
                    "SELECT warestat_date(date) FROM invoices WHERE id="+generated.id())),
                "Linked draft date was not preserved");
            require("0".equals(value("SELECT COUNT(*) FROM invoice_numbering")),
                "Editing a linked draft consumed a fiscal number");

            int issueYear=draftYear+1;
            java.util.Date chosen=new java.text.SimpleDateFormat("dd/MM/yyyy")
                .parse("11/02/"+issueYear);
            DatabaseManager.beginTransaction(db);
            String actualNumber;
            try {
                actualNumber=InvoiceDialog.saveLinkedInvoiceStatus(
                    db,generated.id(),"Draft","Issued",chosen);
                db.commit();
            } catch(Exception error) {db.rollback();throw error;}
            finally {db.setAutoCommit(true);}

            require(actualNumber.startsWith(issueYear+"/"),
                "Invoice year did not follow the chosen invoice date");
            require(actualNumber.equals(value("SELECT number FROM invoices WHERE id="+generated.id())),
                "New invoice number was not saved");
            require(("Issued|"+issueYear+"-02-11").equals(
                    value("SELECT status||'|'||warestat_date(date) FROM invoices WHERE id="+generated.id())),
                "Chosen invoice date was lost at issuance");
            require("2024-01-15".equals(value("SELECT warestat_date(order_date) FROM orders WHERE id=1")),
                "Issuing the invoice changed its original order date");

            String originalNumber=actualNumber;
            InvoiceDialog.saveLinkedInvoiceStatus(db,generated.id(),"Issued","Canceled",null);
            require(("Canceled|"+issueYear+"-02-11").equals(
                    value("SELECT status||'|'||warestat_date(date) FROM invoices WHERE id="+generated.id()))
                    && originalNumber.equals(value("SELECT number FROM invoices WHERE id="+generated.id())),
                "Already issued invoice's date or number changed");
        });
        test("standalone invoice drafts do not consume numbering and use their chosen issue year",()->{
            fresh();
            ID draft=invoice();
            put(draft,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            call(draft,"saveInvoice");
            String draftNumber=value("SELECT number FROM invoices WHERE id=1");
            require("DRAFT-1".equals(draftNumber) && "Draft".equals(value("SELECT status FROM invoices WHERE id=1")),
                "A new standalone draft received a fiscal invoice number");
            require("0".equals(value("SELECT COUNT(*) FROM invoice_numbering")),
                "Saving a standalone draft consumed a fiscal number");

            ID edit=invoice();
            put(edit,"invoice",new Invoice(1,draftNumber,new java.util.Date(),1,"",30,6.6,36.6,"Draft"));
            put(edit,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            put(edit,"dateField",new DatePickerField("08/09/2031"));
            call(edit,"saveInvoice");
            require("DRAFT-1".equals(value("SELECT number FROM invoices WHERE id=1"))
                    && "2031-09-08".equals(value("SELECT warestat_date(date) FROM invoices WHERE id=1")),
                "Editing a draft lost its date or prematurely numbered it");
            require("0".equals(value("SELECT COUNT(*) FROM invoice_numbering")),
                "Updating a standalone draft consumed a fiscal number");

            ID issued=invoice();
            put(issued,"invoice",new Invoice(1,"DRAFT-1",new java.util.Date(),1,"",30,6.6,36.6,"Draft"));
            put(issued,"statusCombo",new JComboBox<>(new String[]{"Issued"}));
            put(issued,"dateField",new DatePickerField("12/03/2032"));
            call(issued,"saveInvoice");
            require("2032/0001".equals(value("SELECT number FROM invoices WHERE id=1"))
                    && "Issued".equals(value("SELECT status FROM invoices WHERE id=1"))
                    && "2032-03-12".equals(value("SELECT warestat_date(date) FROM invoices WHERE id=1")),
                "Issuing a standalone draft failed to date or number it");
            require("1".equals(value("SELECT last_number FROM invoice_numbering WHERE year=2032")),
                "Standalone issuance did not advance the correct yearly sequence");

            require("2032/0001".equals(value("SELECT number FROM invoices WHERE id=1")),
                "Issued invoice number unexpectedly changed");
        });
        test("directly issued invoices are numbered sequentially despite drafts",()->{
            fresh();
            ID direct=invoice();
            put(direct,"dateField",new DatePickerField("15/06/2034"));
            call(direct,"saveInvoice");
            String number=value("SELECT number FROM invoices WHERE id=1");
            require("2034/0001".equals(number)
                && "Issued".equals(value("SELECT status FROM invoices WHERE id=1"))
                && "2034-06-15".equals(value("SELECT warestat_date(date) FROM invoices WHERE id=1")),
                "Direct issuance did not number the invoice in the date's year");

            // Even a stale editor claiming an issued invoice is still a draft
            // cannot modify its number or historical date in the database.
            ID stale=invoice();
            put(stale,"invoice",new Invoice(1,number,new java.util.Date(),1,"",30,6.6,36.6,"Draft"));
            put(stale,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            put(stale,"dateField",new DatePickerField("02/01/1999"));
            rejectedSave(stale,"saveInvoice","invoiceSaved");
            require(number.equals(value("SELECT number FROM invoices WHERE id=1"))
                    && "2034-06-15".equals(value("SELECT warestat_date(date) FROM invoices WHERE id=1")),
                "A stale draft editor changed an issued invoice");

            // A second draft in the same year does not advance the fiscal sequence.
            ID draft=invoice();
            put(draft,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            put(draft,"dateField",new DatePickerField("16/06/2034"));
            call(draft,"saveInvoice");
            require("DRAFT-2".equals(value("SELECT number FROM invoices WHERE id=2"))
                    && "1".equals(value("SELECT last_number FROM invoice_numbering WHERE year=2034")),
                "A later draft consumed the sequence after an issued document");

            ID secondIssued=invoice();
            put(secondIssued,"dateField",new DatePickerField("17/06/2034"));
            call(secondIssued,"saveInvoice");
            require("2034/0002".equals(value("SELECT number FROM invoices WHERE id=3")),
                "Issued invoice numbering was interrupted by a draft");
        });
        test("product price preview updates live for gross price VAT and purchase cost",()->{
            fresh();PD dialog=OperationalRegressionTest.productEditor(0);put(dialog,"product",null);
            JPanel preview=(JPanel)call(dialog,"createPricePreview");
            JTextField price=(JTextField)get(dialog,"priceField");
            JTextField vat=(JTextField)get(dialog,"vatRateField");
            JTextField cost=(JTextField)get(dialog,"acquisitionCostField");
            price.setText("122");vat.setText("22");cost.setText("80");
            JLabel breakdown=(JLabel)get(dialog,"priceBreakdownLabel");
            JLabel margin=(JLabel)get(dialog,"estimatedMarginLabel");
            require(breakdown.getText().equals(I18n.format("{0} net + {1} VAT",SettingsPanel.formatCurrency(100),SettingsPanel.formatCurrency(22))),"Net/VAT preview is incorrect");
            require(margin.getText().contains("20%") && margin.getText().contains(SettingsPanel.formatCurrency(20)),"Margin was confused with markup");
            cost.setText("120");require(margin.getText().contains("-20%"),"Loss is hidden");
            vat.setText("0");require(breakdown.getText().contains(SettingsPanel.formatCurrency(122)),"VAT edit did not update preview");
            price.setText("100,00");cost.setText("80");require(margin.getText().contains("20%"),"Decimal comma input did not update preview");
            price.setText("0");require(!margin.getText().contains("%"),"Zero price produced an invalid margin");
            price.setText("100");cost.setText("");require(!margin.getText().contains("%"),"Missing cost retained a stale margin");
            price.setText("-");require(breakdown.getText().isBlank() && margin.getText().isBlank(),"Incomplete input retained old estimates");
            require("10.0".equals(value("SELECT sale_price_inc_vat FROM products WHERE id=1")),"Preview wrote to the database");
        });
        test("existing product margin preview uses preferred supplier cost and translates",()->{
            fresh();PD dialog=OperationalRegressionTest.productEditor(10);
            ((Product)get(dialog,"product")).setAcquisitionCost(95);
            put(dialog,"preferredSupplierCurrentCost",80.0);
            call(dialog,"createPricePreview");
            ((JTextField)get(dialog,"priceField")).setText("122");
            JLabel margin=(JLabel)get(dialog,"estimatedMarginLabel");
            require(margin.getText().contains("20%"),"Existing preview did not use preferred supplier cost");
            String previous=I18n.getLanguage();
            try {for(String language:List.of("it","es","fr","de","pl","pt_BR")) {
                I18n.setLanguage(language);call(dialog,"updatePricePreview");
                require(!margin.getText().contains("Estimated margin"),"Missing margin translation in "+language);
            }} finally {I18n.setLanguage(previous);}
        });
        test("122 gross sale keeps 100 revenue 22 VAT and 40 FIFO profit through invoicing",()->{
            fresh();
            PD product=OperationalRegressionTest.productEditor(10);
            ((JTextField)get(product,"priceField")).setText("122");
            ((JTextField)get(product,"vatRateField")).setText("22");
            call(product,"saveProduct");
            require("100.0|122.0".equals(value("SELECT sale_price_ex_vat||'|'||sale_price_inc_vat FROM products WHERE id=1")),"Product gross input did not produce net 100");
            sql("UPDATE products SET acquisition_cost=60 WHERE id=1");seedOpeningLots();
            OD dialog=order(false,1,"Completed");
            DefaultTableModel rows=(DefaultTableModel)get(dialog,"itemsTableModel");
            rows.setValueAt("122",0,3);rows.setValueAt("22",0,5);
            put(dialog,"paymentStatusCombo",new JComboBox<>(new String[]{"Paid"}));
            call(dialog,"saveOrder");
            require("100.0|122.0|122.0|PAID".equals(value("SELECT total_ex_vat||'|'||total_inc_vat||'|'||paid_amount||'|'||payment_status FROM orders")),"Order gross payment or net revenue incorrect");
            require("60.0".equals(value("SELECT cost_total FROM order_details")) && qty()==9,"FIFO or stock incorrect");
            sql("UPDATE products SET vat_rate=10,sale_price_inc_vat=110 WHERE id=1");
            int invoice=invoiceOrder(1);
            require("100.0|22.0|122.0".equals(value("SELECT taxable_amount||'|'||vat||'|'||total FROM invoices")),"Invoice used current product VAT");
            exportInvoice(invoice,"vat-122-100-22.pdf");
            DashboardPanel dashboard=new DashboardPanel(null);call(dashboard,"loadKPIData");
            require(OperationalRegressionTest.kpi(dashboard,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(100)),"Dashboard includes VAT as revenue");
            SalesReportPanel report=new SalesReportPanel();call(report,"loadReportData");
            require((double)get(report,"summaryProfit")==40.0,"Gross profit includes VAT");
        });
        test("editing paid pending orders preserves receipts and derives payment status",()->{
            fresh();OD first=order(false,1,"New");
            put(first,"paymentStatusCombo",new JComboBox<>(new String[]{"Paid"}));call(first,"saveOrder");
            OD edit=order(true,2,"New");put(edit,"order",loadOrder(1));
            put(edit,"paymentStatusCombo",new JComboBox<>(new String[]{"Paid"}));call(edit,"saveOrder");
            require("20.0|10.0|PARTIALLY_PAID".equals(value("SELECT total_inc_vat||'|'||paid_amount||'|'||payment_status FROM orders")),"Quantity edit invented a payment");
            for(String[] sample:new String[][]{{"0","NOT_PAID"},{"10","PARTIALLY_PAID"},{"20","PAID"}}) {
                OD partial=order(true,2,"New");put(partial,"order",loadOrder(1));
                put(partial,"paymentStatusCombo",new JComboBox<>(new String[]{"Partially Paid"}));
                put(partial,"paidAmountField",new JTextField(sample[0]));call(partial,"saveOrder");
                require(sample[1].equals(value("SELECT payment_status FROM orders")),"Payment state disagrees with amount received");
            }
        });
        test("draft invoice content edits preserve recorded receipts",()->{
            fresh();ID first=invoice();put(first,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            put(first,"paymentStatusCombo",new JComboBox<>(new String[]{"Paid"}));call(first,"saveInvoice");
            ID edit=invoice();put(edit,"invoice",new Invoice(1,"DRAFT",new java.util.Date(),1,"",30,6.6,36.6,"Draft"));
            put(edit,"statusCombo",new JComboBox<>(new String[]{"Draft"}));
            put(edit,"paymentStatusCombo",new JComboBox<>(new String[]{"Not Paid","Partially Paid","Paid"}));
            call(edit,"loadPaymentData");
            ((DefaultTableModel)get(edit,"itemsTableModel")).setValueAt(6,0,2);call(edit,"saveInvoice");
            require("73.2|36.6|PARTIALLY_PAID".equals(value("SELECT total||'|'||paid_amount||'|'||payment_status FROM invoices")),"Draft edit result: "+value("SELECT total||'|'||paid_amount||'|'||payment_status FROM invoices"));
        });
        test("currency conversion preserves receipt amounts and order line net reconciliation",()->{
            fresh();
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat,payment_status,paid_amount) VALUES(1,1,0,'Completed',100,122,'PAID',100)");
            sql("INSERT INTO order_details(order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate) VALUES(1,1,1,100,122,22)");
            sql("INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status,payment_status,paid_amount) VALUES(1,'DRAFT',0,1,100,22,122,'Draft','PAID',100)");
            sql("INSERT INTO invoice_details(invoice_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate,total) VALUES(1,1,1,100,122,22,100)");
            DatabaseCurrency.change("USD",DatabaseCurrency.ChangeMode.CONVERT,new BigDecimal("1.23"));
            require("123.0|PARTIALLY_PAID".equals(value("SELECT paid_amount||'|'||payment_status FROM orders")),"Order currency conversion invented money");
            require("123.0|PARTIALLY_PAID".equals(value("SELECT paid_amount||'|'||payment_status FROM invoices")),"Invoice currency conversion invented money");
            DatabaseCurrency.change("EUR",DatabaseCurrency.ChangeMode.CONVERT,new BigDecimal("0.17"));
            require("0".equals(value("SELECT COUNT(*) FROM orders o WHERE ABS(total_ex_vat-(SELECT SUM(warestat_line_total(quantity,sale_unit_price_ex_vat)) FROM order_details d WHERE d.order_id=o.id))>0.004")),"Converted order header differs from net lines");
        });
        test("legacy subcent order migration produces a reconciling invoice and unchanged FIFO cost",()->{
            fresh();oldSchema();
            sql("UPDATE products SET price=2.675,vat_rate=22 WHERE id=1");
            sql("INSERT INTO orders(id,customer_id,order_date,status,total,payment_status,paid_amount) VALUES(1,1,0,'Completed',8.03,'PAID',8.03)");
            sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price,cost_total) VALUES(1,1,3,2.675,3)");
            DatabaseMigrationManager.migrate(db);int id=invoiceOrder(1);
            require("8.03|1.77|9.8".equals(value("SELECT taxable_amount||'|'||vat||'|'||total FROM invoices")),"Migrated rounding does not reconcile");
            require("2.675|3.0".equals(value("SELECT sale_unit_price_ex_vat||'|'||cost_total FROM order_details")),"Historical net or FIFO cost changed");
            exportInvoice(id,"vat-legacy-rounding.pdf");
        });
        test("pre-017 schema migration preserves money and migrates once",()->{
            fresh();oldSchema();
            sql("UPDATE products SET price=100,vat_rate=22 WHERE id=1");
            sql("INSERT INTO orders(id,customer_id,order_date,status,total,payment_status,paid_amount) VALUES(1,1,0,'Completed',100,'PAID',100)");
            sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price,cost_total) VALUES(1,1,1,100,60)");
            // Internal schema migration scenario, not a user-imported 1.2 backup.
            DatabaseMigrationManager.migrate(db);
            require("100.0|122.0".equals(value("SELECT sale_price_ex_vat||'|'||sale_price_inc_vat FROM products WHERE id=1")),"Backup product repriced");
            require("100.0|PARTIALLY_PAID".equals(value("SELECT paid_amount||'|'||payment_status FROM orders")),"Backup import invented money");
            DatabaseMigrationManager.migrate(db);
            require("1".equals(value("SELECT COUNT(*) FROM schema_migrations WHERE version='017'")),"Migration reran");
            require("60.0".equals(value("SELECT cost_total FROM order_details")),"Import altered historical cost");
        });
        test("all VAT labels translate across the seven supported languages",()->{
            String previous=I18n.getLanguage();
            try {for(String language:List.of("en","it","es","fr","de","pl","pt_BR")) {
                I18n.setLanguage(language);
                for(String key:List.of("Sale Price incl. VAT","Customer sale price incl. VAT","Unit Price incl. VAT","Total incl. VAT","Unit price excl. VAT","Total excl. VAT","Stock at net sale price"))
                    require(language.equals("en") || !I18n.tr(key).equals(key),"Missing VAT label: "+language+" "+key);
            }} finally {I18n.setLanguage(previous);}
        });
        test("population script matches final schema and invoice totals",()->{
            fresh();
            try(Statement s=db.createStatement()){s.executeUpdate(Files.readString(Path.of("script_populate.sql")));}
            require(Integer.parseInt(value("SELECT COUNT(*) FROM products"))>=120,"Population script incomplete");
            require("0".equals(value("SELECT COUNT(*) FROM orders o WHERE ABS(total_inc_vat-(SELECT SUM(warestat_line_total(quantity,sale_unit_price_inc_vat)) FROM order_details d WHERE d.order_id=o.id))>0.004")),"Demo order gross mismatch");
            require("0".equals(value("SELECT COUNT(*) FROM invoices i WHERE ABS(total-(SELECT SUM(warestat_line_total(quantity,sale_unit_price_inc_vat)) FROM invoice_details d WHERE d.invoice_id=i.id))>0.004")),"Demo invoice gross mismatch");
            exportInvoice(1,"vat-demo.pdf");
        });
    }
}
