import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates a non-fiscal proforma directly from a customer order.
 * No invoice or other commercial document is persisted.
 */
public final class ProformaPDFGenerator {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    private ProformaPDFGenerator() {}

    public static void generateAndSave(int orderId, Component parent) {
        try {
            ProformaData data = load(orderId);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(I18n.tr("Save Proforma PDF"));
            chooser.setFileFilter(new FileNameExtensionFilter(I18n.tr("PDF Files"), "pdf"));

            String lastDirectory = SettingsPanel.getGlobalSetting(
                "pdf_last_directory",
                SettingsPanel.getGlobalSetting("pdf_default_directory", System.getProperty("user.home")));
            if (lastDirectory != null && !lastDirectory.isBlank()) {
                File dir = new File(lastDirectory);
                if (dir.isDirectory()) chooser.setCurrentDirectory(dir);
            }

            String customer = safeFilePart(data.customerName());
            String date = DATE_FORMAT.format(data.orderDate()).replace("/", "-");
            chooser.setSelectedFile(new File(String.format(
                "Proforma_Order_%d_%s_%s.pdf", orderId,
                customer.isBlank() ? "Customer" : customer, date)));

            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                file = new File(file.getAbsolutePath() + ".pdf");
            }
            if (file.getParent() != null) {
                SettingsPanel.setGlobalSetting("pdf_last_directory", file.getParent());
            }

            generatePDF(orderId, file);

            Dialogs.showMessageDialog(parent,
                I18n.tr("Proforma PDF generated successfully!") + "\n" +
                    I18n.tr("Saved to") + ": " + file.getAbsolutePath(),
                I18n.tr("PDF Generated"), JOptionPane.INFORMATION_MESSAGE);

            int open = Dialogs.showConfirmDialog(parent,
                I18n.tr("Would you like to open the PDF file?"),
                I18n.tr("Open PDF"), JOptionPane.YES_NO_OPTION);
            if (open == JOptionPane.YES_OPTION) {
                try {
                    Desktop.getDesktop().open(file);
                } catch (Exception e) {
                    Dialogs.showMessageDialog(parent,
                        I18n.tr("Cannot open PDF automatically. File saved to") + ":\n" +
                            file.getAbsolutePath(),
                        I18n.tr("Information"), JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(parent,
                I18n.tr("Error generating proforma PDF") + ": " +
                    (e.getMessage() == null ? "" : I18n.tr(e.getMessage())),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    static void generatePDF(int orderId, File outputFile) throws IOException {
        try {
            ProformaData data = load(orderId);
            try (PdfWriter pdf = new PdfWriter("", false, I18n.tr("Page"),
                    I18n.tr("Document generated with WareStat"))) {
                String orderRef = I18n.format("Order #{0}", orderId);
                pdf.setContinuationTitle(I18n.tr("PROFORMA") + " - " + orderRef);
                pdf.invoiceHeader(data.logo(), I18n.tr("PROFORMA"), orderRef,
                    I18n.tr("Date"), DATE_FORMAT.format(data.orderDate()),
                    I18n.tr("Document"), I18n.tr("NON-FISCAL DOCUMENT"),
                    I18n.tr("Currency"), data.currency(),
                    I18n.tr("FROM"), data.companyName(), data.companyLines(),
                    I18n.tr("BILL TO"), data.customerName(), data.customerLines());

                pdf.startTable(new String[]{
                        I18n.tr("Code"), I18n.tr("Description"), I18n.tr("Qty"),
                        I18n.tr("Unit price excl. VAT") + " " + data.currency(),
                        I18n.tr("VAT %"),
                        I18n.tr("Total excl. VAT") + " " + data.currency()},
                    new float[]{1.6f, 3.1f, .7f, 1.6f, .9f, 1.6f});

                for (ProformaLine line : data.lines()) {
                    pdf.row(line.code(), line.name(), Integer.toString(line.quantity()),
                        Numbers.unitText(line.unitPriceExVat()),
                        decimal(line.vatRate()), money(line.totalExVat()));
                }

                pdf.totals(
                    new String[]{
                        I18n.tr("Taxable amount") + ":",
                        I18n.tr("VAT") + ":",
                        I18n.tr("TOTAL") + ":"},
                    new String[]{
                        data.currency() + " " + money(data.taxable()),
                        data.currency() + " " + money(data.vat()),
                        data.currency() + " " + money(data.total())});

                pdf.note(I18n.tr("PROFORMA - NON-FISCAL DOCUMENT"));
                pdf.save(outputFile);
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new IOException(e.getMessage() == null
                ? I18n.tr("Unable to generate a consistent proforma")
                : I18n.tr(e.getMessage()), e);
        }
    }

    private static ProformaData load(int orderId) throws SQLException, IOException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        java.util.Date orderDate;
        String currency = DatabaseCurrency.read(conn);
        if (currency == null || currency.isBlank()) {
            throw new SQLException("Database currency is missing");
        }

        String customerName;
        java.util.List<String> customerLines = new ArrayList<>();
        double savedTaxable;
        double savedTotal;

        try (PreparedStatement p = conn.prepareStatement("""
                SELECT o.order_date,o.status,o.total_ex_vat,o.total_inc_vat,
                       c.first_name,c.last_name,c.address,c.email,c.phone
                FROM orders o
                LEFT JOIN customers c ON c.id=o.customer_id
                WHERE o.id=?
                """)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Order no longer exists");
                String status = r.getString("status");
                if (!OrdersPanel.canGenerateProforma(status)) {
                    throw new SQLException(
                        "Proforma can only be generated for Reservation, New or In Progress customer orders.");
                }
                orderDate = DateUtils.parseDate(r, "order_date");
                if (orderDate == null) throw new SQLException("Invalid order date");
                savedTaxable = Numbers.nonNegative(r.getObject("total_ex_vat"));
                savedTotal = Numbers.nonNegative(r.getObject("total_inc_vat"));
                customerName = joinParts(" ", r.getString("first_name"), r.getString("last_name"));
                addLine(customerLines, r.getString("address"));
                addLine(customerLines, r.getString("email"));
                addLine(customerLines, r.getString("phone"));
            }
        }

        java.util.List<ProformaLine> lines = new ArrayList<>();
        double taxable = 0.0;
        double vat = 0.0;
        double total = 0.0;
        try (PreparedStatement p = conn.prepareStatement("""
                SELECT p.code,p.name,d.quantity,d.sale_unit_price_ex_vat,
                       d.sale_unit_price_inc_vat,d.vat_rate
                FROM order_details d
                LEFT JOIN products p ON p.id=d.product_id
                WHERE d.order_id=?
                ORDER BY d.id
                """)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    int quantity = Numbers.positiveQuantity(r.getObject("quantity"));
                    double unitExVat = Numbers.nonNegative(r.getObject("sale_unit_price_ex_vat"));
                    double unitIncVat = Numbers.nonNegative(r.getObject("sale_unit_price_inc_vat"));
                    double vatRate = Numbers.vat(r.getObject("vat_rate"));
                    double lineNet = Numbers.lineTotal(quantity, unitExVat);
                    double lineGross = SalePrice.lineIncVat(quantity, unitIncVat);
                    double lineVat = Numbers.money(java.math.BigDecimal.valueOf(lineGross)
                        .subtract(java.math.BigDecimal.valueOf(lineNet)));
                    taxable = Numbers.add(taxable, lineNet);
                    vat = Numbers.add(vat, lineVat);
                    total = Numbers.add(total, lineGross);
                    String code = Objects.toString(r.getString("code"), "");
                    String name = Objects.toString(r.getString("name"), "");
                    lines.add(new ProformaLine(code, name, quantity, unitExVat, vatRate, lineNet));
                }
            }
        }
        if (lines.isEmpty()) throw new SQLException("Cannot generate proforma: order has no items");
        if (!sameMoney(taxable, savedTaxable) || !sameMoney(total, savedTotal)) {
            throw new SQLException("Customer order totals do not match its lines");
        }

        String companyName = "";
        java.util.List<String> companyLines = new ArrayList<>();
        byte[] logo = null;
        try (Statement s = conn.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM company_data ORDER BY id LIMIT 1")) {
            if (r.next()) {
                companyName = Objects.toString(r.getString("company_name"), "");
                addLine(companyLines, r.getString("address"));
                addLine(companyLines, joinParts(" ", r.getString("postal_code"), r.getString("city")));
                addLine(companyLines, r.getString("country"));
                addLabeledLine(companyLines, I18n.tr("VAT"), r.getString("vat_number"));
                addLabeledLine(companyLines, I18n.tr("Tax code"), r.getString("tax_code"));
                addLine(companyLines, r.getString("phone"));
                addLine(companyLines, r.getString("email"));
                addLine(companyLines, r.getString("website"));
                String logoPath = r.getString("logo_path");
                if (logoPath != null && !logoPath.isBlank() && Files.isRegularFile(Path.of(logoPath))) {
                    logo = Files.readAllBytes(Path.of(logoPath));
                }
            }
        }

        return new ProformaData(orderDate, currency, companyName, companyLines, logo,
            customerName, customerLines, lines, taxable, vat, total);
    }

    private static boolean sameMoney(double left, double right) {
        return java.math.BigDecimal.valueOf(left).setScale(2, java.math.RoundingMode.HALF_UP)
            .compareTo(java.math.BigDecimal.valueOf(right)
                .setScale(2, java.math.RoundingMode.HALF_UP)) == 0;
    }

    private static void addLine(java.util.List<String> lines, String value) {
        if (value != null && !value.trim().isEmpty()) lines.add(value.trim());
    }

    private static void addLabeledLine(java.util.List<String> lines, String label, String value) {
        if (value != null && !value.trim().isEmpty()) lines.add(label + ": " + value.trim());
    }

    private static String joinParts(String separator, String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value.trim());
        }
        return result.toString();
    }

    private static String safeFilePart(String value) {
        return Objects.toString(value, "").trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private static String money(double value) {
        return java.math.BigDecimal.valueOf(value)
            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String decimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private record ProformaLine(String code, String name, int quantity,
            double unitPriceExVat, double vatRate, double totalExVat) {}

    private record ProformaData(java.util.Date orderDate, String currency,
            String companyName, java.util.List<String> companyLines, byte[] logo,
            String customerName, java.util.List<String> customerLines,
            java.util.List<ProformaLine> lines,
            double taxable, double vat, double total) {}
}
