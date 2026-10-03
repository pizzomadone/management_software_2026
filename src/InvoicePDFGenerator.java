import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;

public class InvoicePDFGenerator {
    private static final float MARGIN = 40f;
    private static final float LINE_HEIGHT = 12f;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    private Invoice invoice;
    private Customer customer;
    private CompanyData companyData;
    private List<InvoiceItem> invoiceItems;

    public InvoicePDFGenerator(Invoice invoice, Customer customer) {
        this.invoice = invoice;
        this.customer = customer;
        this.companyData = CompanyData.getInstance();
        this.invoiceItems = new ArrayList<>();

    }

    public void generateAndSave(Component parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18n.tr("Save Invoice PDF"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(I18n.tr("PDF Files"), "pdf"));

        // Use settings system for last directory
        String lastDirectory = getLastDirectory();
        if (lastDirectory != null && !lastDirectory.trim().isEmpty()) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setCurrentDirectory(dir);
            }
        }

        // Improved file name
        String invoiceNumber = invoice.getNumber();
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            invoiceNumber = "INV_" + invoice.getId();
        }
        invoiceNumber = invoiceNumber.replaceAll("[^a-zA-Z0-9._-]", "_");

        String customerName = "";
        if (customer != null && customer.getLastName() != null && !customer.getLastName().trim().isEmpty()) {
            customerName = customer.getLastName().trim();
        } else if (customer != null && customer.getFirstName() != null && !customer.getFirstName().trim().isEmpty()) {
            customerName = customer.getFirstName().trim();
        } else {
            customerName = "Customer_" + invoice.getCustomerId();
        }
        customerName = customerName.replaceAll("[^a-zA-Z0-9._-]", "_");

        String dateString = "";
        try {
            if (invoice.getDate() != null) {
                dateString = DATE_FORMAT.format(invoice.getDate());
            } else {
                dateString = DATE_FORMAT.format(new Date());
            }
            dateString = dateString.replace("/", "-");
        } catch (Exception e) {
            dateString = String.valueOf(System.currentTimeMillis() / 1000);
        }

        String defaultName = String.format("Invoice_%s_%s_%s.pdf",
            invoiceNumber, customerName, dateString);

        fileChooser.setSelectedFile(new File(defaultName));

        if (fileChooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".pdf")) {
                file = new File(file.getAbsolutePath() + ".pdf");
            }

            // Save last directory using settings system
            saveLastDirectory(file.getParent());

            try {
                generatePDF(file);
                Dialogs.showMessageDialog(parent,
                    I18n.tr("Invoice PDF generated successfully!") + "\n" + I18n.tr("Saved to") + ": " + file.getAbsolutePath(),
                    I18n.tr("PDF Generated"),
                    JOptionPane.INFORMATION_MESSAGE);

                int choice = Dialogs.showConfirmDialog(parent,
                    I18n.tr("Would you like to open the PDF file?"),
                    I18n.tr("Open PDF"),
                    JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (Exception e) {
                        Dialogs.showMessageDialog(parent,
                            I18n.tr("Cannot open PDF automatically. File saved to") + ":\n" + file.getAbsolutePath(),
                            I18n.tr("Information"),
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(parent,
                    I18n.tr("Error generating PDF") + ": " + e.getMessage(),
                    I18n.tr("Error"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Get the last used PDF directory from settings
     */
    private String getLastDirectory() {
        return SettingsPanel.getGlobalSetting("pdf_last_directory",
               SettingsPanel.getGlobalSetting("pdf_default_directory", System.getProperty("user.home")));
    }

    /**
     * Save the last used PDF directory to settings
     */
    private void saveLastDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            SettingsPanel.setGlobalSetting("pdf_last_directory", directory);
        }
    }

    private void generatePDF(File outputFile) throws IOException {
        try {
            Connection conn=DatabaseManager.getInstance().getConnection();
            java.util.Properties company;
            byte[] logo; String currency; boolean legacy;
            try(PreparedStatement p=conn.prepareStatement("SELECT * FROM invoices WHERE id=?")) {
                p.setInt(1,invoice.getId());try(ResultSet r=p.executeQuery()) {
                    if(!r.next())throw new SQLException("Invoice no longer exists");
                    invoice=new Invoice(r.getInt("id"),r.getString("number"),DateUtils.parseDate(r,"date"),
                        r.getInt("customer_id"),"",r.getDouble("taxable_amount"),r.getDouble("vat"),r.getDouble("total"),r.getString("status"));
                    company=InvoiceSnapshots.decode(r.getString("company_snapshot"));
                    currency=r.getString("currency_code");DatabaseCurrency.codeFromSetting(currency);
                    logo=r.getBytes("logo_snapshot");legacy=r.getInt("snapshot_version")==0;
                }
            }
            if(invoice.getDate()==null)throw new SQLException("Invalid invoice date");
            customer=InvoiceSnapshots.customer(conn,invoice);
            invoiceItems.clear(); double taxable=0,vat=0;
            try(PreparedStatement p=conn.prepareStatement("SELECT * FROM invoice_details WHERE invoice_id=? ORDER BY id")) {
                p.setInt(1,invoice.getId());try(ResultSet r=p.executeQuery()) {
                    while(r.next()) {
                        int quantity=Numbers.positiveQuantity(r.getObject("quantity"));
                        double priceExVat=Numbers.nonNegative(r.getObject("sale_unit_price_ex_vat"));
                        double priceIncVat=Numbers.nonNegative(r.getObject("sale_unit_price_inc_vat"));
                        double rate=Numbers.vat(r.getObject("vat_rate"));
                        double totalExVat=Numbers.nonNegative(r.getObject("total"));
                        double expectedNet=SalePrice.lineExVat(quantity,priceIncVat,rate);
                        if(Math.abs(expectedNet-totalExVat)>0.004)
                            throw new SQLException("Invoice line totals require reconciliation");
                        double lineGross=SalePrice.lineIncVat(quantity,priceIncVat);
                        double lineVat=Numbers.money(java.math.BigDecimal.valueOf(lineGross)
                            .subtract(java.math.BigDecimal.valueOf(totalExVat)));
                        taxable=Numbers.add(taxable,totalExVat);
                        vat=Numbers.add(vat,lineVat);
                        String code=r.getString("product_code"),name=r.getString("product_name");
                        if(code==null || name==null)throw new SQLException("Invoice product snapshot is missing");
                        invoiceItems.add(new InvoiceItem(r.getInt("id"),invoice.getId(),r.getInt("product_id"),
                            name,code,quantity,priceExVat,priceIncVat,rate,totalExVat));
                    }
                }
            }
            if(invoiceItems.isEmpty() || Math.abs(taxable-invoice.getTaxableAmount())>0.004 || Math.abs(vat-invoice.getVat())>0.004 ||
                    Math.abs(Numbers.add(taxable,vat)-invoice.getTotal())>0.004)
                throw new SQLException("Saved invoice totals do not match its lines. Reconcile the invoice before exporting.");
            try(PdfWriter pdf=new PdfWriter("",false,I18n.tr("Page"),"Documento generato con WareStat")) {
                pdf.setContinuationTitle(I18n.tr("INVOICE")+" "+invoice.getNumber());
                String companyName=company.getProperty("company_name","").trim();
                java.util.List<String> companyLines=new ArrayList<>();
                addLine(companyLines,company.getProperty("address",""));
                addLine(companyLines,joinParts(" ",company.getProperty("postal_code",""),company.getProperty("city","")));
                addLine(companyLines,company.getProperty("country",""));
                addLabeledLine(companyLines,I18n.tr("VAT"),company.getProperty("vat_number",""));
                addLabeledLine(companyLines,I18n.tr("Tax code"),company.getProperty("tax_code",""));
                addLine(companyLines,company.getProperty("phone",""));
                addLine(companyLines,company.getProperty("email",""));
                addLine(companyLines,company.getProperty("website",""));

                String customerName=joinParts(" ",customer.getFirstName(),customer.getLastName());
                java.util.List<String> customerLines=new ArrayList<>();
                addLine(customerLines,customer.getAddress());
                addLine(customerLines,customer.getEmail());
                addLine(customerLines,customer.getPhone());

                pdf.invoiceHeader(logo,I18n.tr("INVOICE"),invoice.getNumber(),
                    I18n.tr("Date"),DATE_FORMAT.format(invoice.getDate()),
                    I18n.tr("Status"),I18n.tr(invoice.getStatus()),
                    I18n.tr("Currency"),currency,
                    I18n.tr("FROM"),companyName,companyLines,
                    I18n.tr("BILL TO"),customerName,customerLines);

                pdf.startTable(new String[]{I18n.tr("Code"),I18n.tr("Description"),I18n.tr("Qty"),I18n.tr("Unit price excl. VAT")+" "+currency,I18n.tr("VAT %"),I18n.tr("Total")+" "+currency},new float[]{1.6f,3.1f,.7f,1.6f,.9f,1.6f});
                for(InvoiceItem item:invoiceItems)pdf.row(item.getProductCode(),item.getProductName(),Integer.toString(item.getQuantity()),
                    Numbers.unitText(item.getUnitPrice()),decimal(item.getVatRate()),money(item.getTotal()));

                pdf.totals(
                    new String[]{I18n.tr("Taxable amount")+":",I18n.tr("VAT")+":",I18n.tr("TOTAL")+":"},
                    new String[]{currency+" "+money(invoice.getTaxableAmount()),currency+" "+money(invoice.getVat()),currency+" "+money(invoice.getTotal())});
                if(legacy)pdf.note(I18n.tr("Legacy invoice: party and product descriptions were captured during upgrade and may differ from the original document."));
                pdf.save(outputFile);
            }
        } catch(SQLException | IllegalArgumentException e) {
            String detail = e.getMessage() == null ? "" : I18n.tr(e.getMessage());
            String message = I18n.tr("Unable to generate a consistent invoice");
            if (!detail.isBlank()) message += ": " + detail;
            throw new IOException(message, e);
        }
    }
    private static void addLine(java.util.List<String> lines,String value) {
        if(value!=null && !value.trim().isEmpty())lines.add(value.trim());
    }
    private static void addLabeledLine(java.util.List<String> lines,String label,String value) {
        if(value!=null && !value.trim().isEmpty())lines.add(label+": "+value.trim());
    }
    private static String joinParts(String separator,String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) {
            if(value==null || value.trim().isEmpty())continue;
            if(result.length()>0)result.append(separator);
            result.append(value.trim());
        }
        return result.toString();
    }
    private static String money(double value) {return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static String decimal(double value) {return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}

    // Static method for easy use
    public static void generateInvoicePDF(Invoice invoice, Customer customer, Component parent) {
        InvoicePDFGenerator generator = new InvoicePDFGenerator(invoice, customer);
        generator.generateAndSave(parent);
    }
}
