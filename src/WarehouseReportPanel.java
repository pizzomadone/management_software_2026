import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Vector;
import java.util.Date;
import java.io.*;

public class WarehouseReportPanel extends JPanel {
    private JTabbedPane tabbedPane;
    private JTable productsTable;
    private JTable movementsTable;
    private DefaultTableModel productsModel;
    private DefaultTableModel movementsModel;
    private SimpleDateFormat dateFormat;
    private DatePickerField startDateField;
    private DatePickerField endDateField;
    private JComboBox<String> typeCombo;

    public WarehouseReportPanel() {
        dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupPanel();
        initComponents();
        loadData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        tabbedPane = new JTabbedPane();

        // Product Status Tab
        JPanel productsPanel = createProductsPanel();
        tabbedPane.addTab("Product Status", productsPanel);

        // Movements Tab
        JPanel movementsPanel = createMovementsPanel();
        tabbedPane.addTab("Movement Analysis", movementsPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createProductsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // General Statistics
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("General Statistics"));

        JLabel totalProductsLabel = new JLabel("Total Products: 0");
        JLabel totalValueLabel = new JLabel("Stock at last purchase cost: " + SettingsPanel.formatCurrency(0));
        totalValueLabel.setToolTipText("Only active products with positive stock are valued at current catalog prices.");
        JLabel lowStockLabel = new JLabel("Low Stock Products: 0");
        JLabel outOfStockLabel = new JLabel("Out of Stock Products: 0");

        statsPanel.add(totalProductsLabel);
        statsPanel.add(totalValueLabel);
        statsPanel.add(lowStockLabel);
        statsPanel.add(outOfStockLabel);

        // Products Table
        String[] columns = {"Code", "Product", "Physical", "Reserved", "Available", "Last Purchase Cost", "Stock at last purchase cost", "Status"};
        productsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productsTable = new JTable(productsModel);
        TableStyleUtil.applyCompact(productsTable);

        // Enable column sorting
        TableSorterUtil.enableSorting(productsTable);

        // Add context menu
        TableInteractionUtil.addContextMenu(productsTable,
            new TableInteractionUtil.TableAction("Export CSV", this::exportProductsToCSV, false),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadProductsData, false)
        );

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton exportPdfButton = new JButton("Export as PDF");
        JButton exportButton = new JButton("Export CSV");
        JButton refreshButton = new JButton("Refresh");

        exportPdfButton.addActionListener(e -> exportProductsToPDF());
        exportButton.addActionListener(e -> exportProductsToCSV());
        refreshButton.addActionListener(e -> loadProductsData());

        buttonPanel.add(exportPdfButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(refreshButton);

        panel.add(statsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(productsTable), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createMovementsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Filters
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filters"));

        startDateField = new DatePickerField(10);
        endDateField = new DatePickerField(10, true);
        typeCombo = new JComboBox<>(new String[]{"All", "INWARD", "OUTWARD"});
        typeCombo.putClientProperty("i18n.values", true);

        // Set default dates (last month)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        endDateField.setText(DateUtils.formatDate(cal.getTime(), dateFormat));
        cal.add(java.util.Calendar.MONTH, -1);
        startDateField.setText(DateUtils.formatDate(cal.getTime(), dateFormat));

        filterPanel.add(new JLabel("From:"));
        filterPanel.add(startDateField);
        filterPanel.add(new JLabel("To:"));
        filterPanel.add(endDateField);
        filterPanel.add(new JLabel("Type:"));
        filterPanel.add(typeCombo);

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> loadMovementsData());
        filterPanel.add(applyButton);

        // Movements Table
        String[] columns = {"Date", "Product", "Type", "Quantity", "Reason", "Document"};
        movementsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        movementsTable = new JTable(movementsModel);
        TableStyleUtil.applyCompact(movementsTable);

        // Enable column sorting with date support (column 0 is "Date")
        TableSorterUtil.enableSorting(movementsTable, new int[]{0}, dateFormat);

        // Add context menu
        TableInteractionUtil.addContextMenu(movementsTable,
            new TableInteractionUtil.TableAction("Export CSV", this::exportMovementsToCSV, false)
        );

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton exportPdfButton = new JButton("Export as PDF");
        JButton exportButton = new JButton("Export CSV");

        exportPdfButton.addActionListener(e -> exportMovementsToPDF());
        exportButton.addActionListener(e -> exportMovementsToCSV());

        buttonPanel.add(exportPdfButton);
        buttonPanel.add(exportButton);

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(movementsTable), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadData() {
        loadProductsData();
        loadMovementsData();
    }

    private boolean productsValid, movementsValid;

    private void loadProductsData() {
        productsValid=false;
        productsModel.setRowCount(0);
        JPanel statsPanel = (JPanel)((JPanel)tabbedPane.getComponentAt(0)).getComponent(0);
        for (Component component : statsPanel.getComponents())
            if (component instanceof JLabel label) label.setText("—");
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*, COALESCE(sm.minimum_quantity, 0) AS minimum_quantity
                FROM products p
                LEFT JOIN minimum_stock sm ON sm.product_id = p.id
                WHERE p.active=1
                ORDER BY p.name
            """;

            int totalProducts = 0;
            double totalValue = 0;
            int lowStock = 0;
            int outOfStock = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("code"));
                    row.add(rs.getString("name"));

                    int physicalStock = Numbers.quantity(rs.getObject("quantity"));
                    int reservedStock = Numbers.quantity(rs.getObject("reserved_quantity"));
                    long availableStock = (long) physicalStock - reservedStock;
                    double price = Numbers.nonNegative(rs.getObject("acquisition_cost"));
                    double totalValueProduct = Numbers.stockValue(physicalStock, price);
                    int minQuantity = rs.getInt("minimum_quantity");

                    row.add(physicalStock);
                    row.add(reservedStock);
                    row.add(availableStock);
                    row.add(Numbers.unitText(price));
                    row.add(Numbers.moneyText(totalValueProduct));

                    // Stock thresholds are based on what is actually available
                    // after customer reservations. Valuation remains physical stock.
                    String status;
                    if (availableStock <= 0) {
                        status = "OUT OF STOCK";
                        outOfStock++;
                    } else if (minQuantity > 0 && availableStock < minQuantity) {
                        status = "LOW STOCK";
                        lowStock++;
                    } else {
                        status = "OK";
                    }
                    row.add(status);

                    productsModel.addRow(row);
                    totalProducts++;
                    totalValue = Numbers.add(totalValue,totalValueProduct);
                }
            }

            // Update statistics
            updateStatistics(totalProducts, totalValue, lowStock, outOfStock);
            productsValid=true;

        } catch (Exception e) {
            productsModel.setRowCount(0);
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatistics(int totalProducts, double totalValue, int lowStock, int outOfStock) {
        try {
            // Get the stats panel from the products tab
            JPanel productsTab = (JPanel) tabbedPane.getComponentAt(0);
            JPanel statsPanel = (JPanel) productsTab.getComponent(0);

            if (statsPanel.getComponentCount() >= 4) {
                updateLocalizedStatistic((JLabel) statsPanel.getComponent(0), "Total Products", totalProducts);
                updateLocalizedStatistic((JLabel) statsPanel.getComponent(1), "Stock at last purchase cost",
                    SettingsPanel.formatCurrency(totalValue));
                updateLocalizedStatistic((JLabel) statsPanel.getComponent(2), "Low Stock Products", lowStock);
                updateLocalizedStatistic((JLabel) statsPanel.getComponent(3), "Out of Stock Products", outOfStock);
            }
        } catch (Exception e) {
            // If updating fails, just log it - not critical
            System.err.println("Failed to update statistics: " + e.getMessage());
        }
    }

    private void updateLocalizedStatistic(JLabel label, String name, Object value) {
        label.setText(I18n.tr(name) + ": " + value);
    }

    private void loadMovementsData() {
        movementsValid=false;
        movementsModel.setRowCount(0);
        try {
            String startDateText = startDateField.getText().trim();
            String endDateText = endDateField.getText().trim();
            String selectedType = (String) typeCombo.getSelectedItem();

            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT m.*, p.name as product_name
                FROM warehouse_movements m
                LEFT JOIN products p ON m.product_id = p.id
                WHERE 1=1
            """;

            // Build query based on filters
            boolean hasDateFilter = false;
            boolean hasTypeFilter = false;
            Date startDate = null;
            Date endDate = null;

            if (!startDateText.isEmpty() || !endDateText.isEmpty()) {
                if (startDateText.isEmpty() || endDateText.isEmpty()) throw new IllegalArgumentException("Enter both dates for the movement report");
                try {
                    startDate = DateUtils.parseDate(startDateText, dateFormat);
                    endDate = DateUtils.parseDate(endDateText, dateFormat);
                    if (startDate != null && endDate != null) {
                        if(startDate.after(endDate)) throw new IllegalArgumentException("Start date must not follow end date");
                        query += " AND " + DateUtils.sqlDate("m.date") + " >= ? AND " + DateUtils.sqlDate("m.date") + " <= ?";
                        hasDateFilter = true;
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid movement report dates",e);
                }
            }

            if (selectedType != null && !"All".equals(selectedType)) {
                query += " AND m.type = ?";
                hasTypeFilter = true;
            }

            query += " ORDER BY m.date DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                int paramIndex = 1;

                if (hasDateFilter && startDate != null && endDate != null) {
                    pstmt.setString(paramIndex++, DateUtils.formatDate(startDate, new SimpleDateFormat("yyyy-MM-dd")));
                    pstmt.setString(paramIndex++, DateUtils.formatDate(endDate, new SimpleDateFormat("yyyy-MM-dd")));
                }

                if (hasTypeFilter) {
                    pstmt.setString(paramIndex, selectedType);
                }

                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();

                    Date movementDate = DateUtils.parseDate(rs, "date");
                    if (movementDate != null) {
                        row.add(DateUtils.formatDate(movementDate, dateFormat));
                    } else {
                        row.add("N/A");
                    }

                    String productName = rs.getString("product_name");
                    row.add(productName != null ? productName : "Product N/A");
                    row.add(rs.getString("type"));
                    row.add(rs.getInt("quantity"));
                    row.add(rs.getString("reason"));

                    String document = rs.getString("document_type");
                    if (document != null && !document.isEmpty()) {
                        String docNumber = rs.getString("document_number");
                        if (docNumber != null && !docNumber.isEmpty()) {
                            document += " " + docNumber;
                        }
                    } else {
                        document = "";
                    }
                    row.add(document);

                    movementsModel.addRow(row);
                }
            }
            movementsValid=true;
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading movements: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportProductsToPDF() {
        loadProductsData();if(!productsValid)return;
        String fileName = String.format("warehouse_products_%s.pdf", new SimpleDateFormat("yyyyMMdd").format(new Date()));

        ReportPDFGenerator pdfGenerator = new ReportPDFGenerator(
            productsModel,
            "Warehouse Report - Product Status",
            I18n.tr("Generated") + ": " + new SimpleDateFormat("dd/MM/yyyy").format(new Date()),
            fileName
        );

        pdfGenerator.generateAndSave(this);
    }

    private void exportMovementsToPDF() {
        loadMovementsData();if(!movementsValid)return;
        String subtitle = I18n.tr("From") + ": " +
            (startDateField != null ? startDateField.getText() : I18n.tr("N/A")) + " " +
            I18n.tr("to") + ": " +
            (endDateField != null ? endDateField.getText() : I18n.tr("N/A"));
        String fileName = String.format("warehouse_movements_%s.pdf", new SimpleDateFormat("yyyyMMdd").format(new Date()));

        ReportPDFGenerator pdfGenerator = new ReportPDFGenerator(
            movementsModel,
            "Warehouse Report - Movement Analysis",
            subtitle,
            fileName
        );

        pdfGenerator.generateAndSave(this);
    }

    private void exportProductsToCSV() {
        loadProductsData();if(!productsValid)return;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save CSV Report");
        fileChooser.setSelectedFile(new File("warehouse_report.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile(), java.nio.charset.StandardCharsets.UTF_8))) {
                // Headers
                for (int i = 0; i < productsModel.getColumnCount(); i++) {
                    writer.print(Csv.cell(productsModel.getColumnName(i)));
                    writer.print(i < productsModel.getColumnCount() - 1 ? "," : "\n");
                }

                // Data
                for (int row = 0; row < productsModel.getRowCount(); row++) {
                    for (int col = 0; col < productsModel.getColumnCount(); col++) {
                        String value = Csv.cell(productsModel.getValueAt(row, col));
                        writer.print(value);
                        writer.print(col < productsModel.getColumnCount() - 1 ? "," : "\n");
                    }
                }

                Csv.finish(writer);
                Dialogs.showMessageDialog(this,
                    "Report exported successfully",
                    "Export Completed",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportMovementsToCSV() {
        loadMovementsData();if(!movementsValid)return;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Movements CSV Report");
        fileChooser.setSelectedFile(new File("warehouse_movements_report.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile(), java.nio.charset.StandardCharsets.UTF_8))) {
                // Headers
                writer.println("Warehouse Movements Report");
                writer.println("Period: " + startDateField.getText() + " - " + endDateField.getText());
                writer.println();

                // Column headers
                for (int i = 0; i < movementsModel.getColumnCount(); i++) {
                    writer.print(Csv.cell(movementsModel.getColumnName(i)));
                    writer.print(i < movementsModel.getColumnCount() - 1 ? "," : "\n");
                }

                // Data
                for (int row = 0; row < movementsModel.getRowCount(); row++) {
                    for (int col = 0; col < movementsModel.getColumnCount(); col++) {
                        String value = Csv.cell(movementsModel.getValueAt(row, col));
                        writer.print(value);
                        writer.print(col < movementsModel.getColumnCount() - 1 ? "," : "\n");
                    }
                }

                Csv.finish(writer);
                Dialogs.showMessageDialog(this,
                    "Movements report exported successfully",
                    "Export Completed",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    public void refreshData() { loadData(); }
}
