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
        fileChooser.setDialogTitle("Save Invoice PDF");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));

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
        if (customer.getLastName() != null && !customer.getLastName().trim().isEmpty()) {
            customerName = customer.getLastName().trim();
        } else if (customer.getFirstName() != null && !customer.getFirstName().trim().isEmpty()) {
            customerName = customer.getFirstName().trim();
        } else {
            customerName = "Customer_" + customer.getId();
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
                JOptionPane.showMessageDialog(parent,
                    "Invoice PDF generated successfully!\nSaved to: " + file.getAbsolutePath(),
                    "PDF Generated",
                    JOptionPane.INFORMATION_MESSAGE);

                int choice = JOptionPane.showConfirmDialog(parent,
                    "Would you like to open the PDF file?",
                    "Open PDF",
                    JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(parent,
                            "Cannot open PDF automatically. File saved to:\n" + file.getAbsolutePath(),
                            "Information",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parent,
                    "Error generating PDF: " + e.getMessage(),
                    "Error",
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
                        double price=Numbers.nonNegative(r.getObject("unit_price")),rate=Numbers.vat(r.getObject("vat_rate"));
                        double total=Numbers.lineTotal(quantity,price);
                        if(Math.abs(total-r.getDouble("total"))>0.004)throw new SQLException("Invoice line totals require reconciliation");
                        taxable=Numbers.add(taxable,total);vat=Numbers.add(vat,Numbers.tax(total,rate));
                        String code=r.getString("product_code"),name=r.getString("product_name");
                        if(code==null || name==null)throw new SQLException("Invoice product snapshot is missing");
                        invoiceItems.add(new InvoiceItem(r.getInt("id"),invoice.getId(),r.getInt("product_id"),name,code,quantity,price,rate,total));
                    }
                }
            }
            if(invoiceItems.isEmpty() || Math.abs(taxable-invoice.getTaxableAmount())>0.004 || Math.abs(vat-invoice.getVat())>0.004 ||
                    Math.abs(Numbers.add(taxable,vat)-invoice.getTotal())>0.004)
                throw new SQLException("Saved invoice totals do not match its lines. Reconcile the invoice before exporting.");
            try(PdfWriter pdf=new PdfWriter("INVOICE "+invoice.getNumber(),false)) {
                pdf.logo(logo);
                pdf.paragraph("Date: "+DATE_FORMAT.format(invoice.getDate())+" | Status: "+invoice.getStatus()+" | Currency: "+currency,false);
                if(legacy)pdf.paragraph("Legacy invoice: party and product descriptions were captured during upgrade and may differ from the original document.",true);
                pdf.paragraph("FROM",true);
                for(String key:java.util.List.of("company_name","address","city","postal_code","country","vat_number","tax_code","phone","email","website")) {
                    String value=company.getProperty(key,"");
                    if(!value.isBlank())pdf.paragraph((key.equals("vat_number")?"VAT: ":key.equals("tax_code")?"Tax code: ":"")+value,false);
                }
                pdf.paragraph("BILL TO",true);
                pdf.paragraph(customer.getFirstName()+" "+customer.getLastName(),true);
                for(String value:java.util.List.of(customer.getAddress(),customer.getEmail(),customer.getPhone()))if(!value.isBlank())pdf.paragraph(value,false);
                pdf.startTable(new String[]{"Code","Description","Qty","Unit price "+currency,"VAT %","Total "+currency},new float[]{1.6f,3.1f,.7f,1.6f,.9f,1.6f});
                for(InvoiceItem item:invoiceItems)pdf.row(item.getProductCode(),item.getProductName(),Integer.toString(item.getQuantity()),
                    decimal(item.getUnitPrice()),decimal(item.getVatRate()),money(item.getTotal()));
                pdf.paragraph("Taxable amount: "+currency+" "+money(invoice.getTaxableAmount()),true);
                pdf.paragraph("VAT: "+currency+" "+money(invoice.getVat()),true);
                pdf.paragraph("TOTAL: "+currency+" "+money(invoice.getTotal()),true);
                pdf.save(outputFile);
            }
        } catch(SQLException | IllegalArgumentException e) {throw new IOException("Unable to generate a consistent invoice: "+e.getMessage(),e);}
    }
    private static String money(double value) {return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static String decimal(double value) {return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}

    // Static method for easy use
    public static void generateInvoicePDF(Invoice invoice, Customer customer, Component parent) {
        InvoicePDFGenerator generator = new InvoicePDFGenerator(invoice, customer);
        generator.generateAndSave(parent);
    }
}
