import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;
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
    private JTextField startDateField;
    private JTextField endDateField;
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
        JLabel totalValueLabel = new JLabel("Total Value: € 0.00");
        JLabel lowStockLabel = new JLabel("Low Stock Products: 0");
        JLabel outOfStockLabel = new JLabel("Out of Stock Products: 0");

        statsPanel.add(totalProductsLabel);
        statsPanel.add(totalValueLabel);
        statsPanel.add(lowStockLabel);
        statsPanel.add(outOfStockLabel);

        // Products Table
        String[] columns = {"Code", "Product", "Quantity", "Unit Value", "Total Value", "Status"};
        productsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productsTable = new JTable(productsModel);

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton printButton = new JButton("Print Report");
        JButton exportButton = new JButton("Export CSV");
        JButton refreshButton = new JButton("Refresh");

        printButton.addActionListener(e -> printProductsReport());
        exportButton.addActionListener(e -> exportProductsToCSV());
        refreshButton.addActionListener(e -> loadProductsData());

        buttonPanel.add(printButton);
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

        startDateField = new JTextField(10);
        endDateField = new JTextField(10);
        typeCombo = new JComboBox<>(new String[]{"All", "INWARD", "OUTWARD"});

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

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton printButton = new JButton("Print Report");
        JButton exportButton = new JButton("Export CSV");

        printButton.addActionListener(e -> printMovementsReport());
        exportButton.addActionListener(e -> exportMovementsToCSV());

        buttonPanel.add(printButton);
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

    private void loadProductsData() {
        productsModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*, COALESCE(sm.minimum_quantity, 0) as minimum_quantity
                FROM products p
                LEFT JOIN minimum_stock sm ON p.id = sm.product_id
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

                    int quantity = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    double totalValueProduct = quantity * price;
                    int minQuantity = rs.getInt("minimum_quantity");

                    row.add(quantity);
                    row.add(String.format("%.2f", price));
                    row.add(String.format("%.2f", totalValueProduct));

                    // Determine status
                    String status;
                    if (quantity <= 0) {
                        status = "OUT OF STOCK";
                        outOfStock++;
                    } else if (minQuantity > 0 && quantity < minQuantity) {
                        status = "LOW STOCK";
                        lowStock++;
                    } else {
                        status = "OK";
                    }
                    row.add(status);

                    productsModel.addRow(row);
                    totalProducts++;
                    totalValue += totalValueProduct;
                }
            }

            // Update statistics
            updateStatistics(totalProducts, totalValue, lowStock, outOfStock);

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
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
                ((JLabel) statsPanel.getComponent(0)).setText("Total Products: " + totalProducts);
                ((JLabel) statsPanel.getComponent(1)).setText(String.format("Total Value: € %.2f", totalValue));
                ((JLabel) statsPanel.getComponent(2)).setText("Low Stock Products: " + lowStock);
                ((JLabel) statsPanel.getComponent(3)).setText("Out of Stock Products: " + outOfStock);
            }
        } catch (Exception e) {
            // If updating fails, just log it - not critical
            System.err.println("Failed to update statistics: " + e.getMessage());
        }
    }

    private void loadMovementsData() {
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

            if (!startDateText.isEmpty() && !endDateText.isEmpty()) {
                try {
                    startDate = DateUtils.parseDate(startDateText, dateFormat);
                    endDate = DateUtils.parseDate(endDateText, dateFormat);
                    if (startDate != null && endDate != null) {
                        query += " AND DATE(m.date) >= DATE(?) AND DATE(m.date) <= DATE(?)";
                        hasDateFilter = true;
                    }
                } catch (Exception e) {
                    // Ignore date parsing errors and load all data
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
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading movements: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printProductsReport() {
        MessageFormat header = new MessageFormat("Warehouse Report - Product Status");
        MessageFormat footer = new MessageFormat("Page {0,number,integer}");
        try {
            productsTable.print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this,
                "Error printing report: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void printMovementsReport() {
        MessageFormat header = new MessageFormat("Warehouse Report - Movement Analysis");
        MessageFormat footer = new MessageFormat("Page {0,number,integer}");
        try {
            movementsTable.print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this,
                "Error printing report: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void exportProductsToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save CSV Report");
        fileChooser.setSelectedFile(new File("warehouse_report.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                // Headers
                for (int i = 0; i < productsModel.getColumnCount(); i++) {
                    writer.print(productsModel.getColumnName(i));
                    writer.print(i < productsModel.getColumnCount() - 1 ? "," : "\n");
                }

                // Data
                for (int row = 0; row < productsModel.getRowCount(); row++) {
                    for (int col = 0; col < productsModel.getColumnCount(); col++) {
                        String value = productsModel.getValueAt(row, col).toString();
                        if (value.contains(",")) {
                            value = "\"" + value + "\"";
                        }
                        writer.print(value);
                        writer.print(col < productsModel.getColumnCount() - 1 ? "," : "\n");
                    }
                }

                JOptionPane.showMessageDialog(this,
                    "Report exported successfully",
                    "Export Completed",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportMovementsToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Movements CSV Report");
        fileChooser.setSelectedFile(new File("warehouse_movements_report.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                // Headers
                writer.println("Warehouse Movements Report");
                writer.println("Period: " + startDateField.getText() + " - " + endDateField.getText());
                writer.println();

                // Column headers
                for (int i = 0; i < movementsModel.getColumnCount(); i++) {
                    writer.print(movementsModel.getColumnName(i));
                    writer.print(i < movementsModel.getColumnCount() - 1 ? "," : "\n");
                }

                // Data
                for (int row = 0; row < movementsModel.getRowCount(); row++) {
                    for (int col = 0; col < movementsModel.getColumnCount(); col++) {
                        String value = movementsModel.getValueAt(row, col).toString();
                        if (value.contains(",")) {
                            value = "\"" + value + "\"";
                        }
                        writer.print(value);
                        writer.print(col < movementsModel.getColumnCount() - 1 ? "," : "\n");
                    }
                }

                JOptionPane.showMessageDialog(this,
                    "Movements report exported successfully",
                    "Export Completed",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
