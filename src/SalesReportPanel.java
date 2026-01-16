import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import javax.swing.border.TitledBorder;
import java.util.Date;
import java.awt.event.ActionEvent;
import java.text.ParseException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.File;
import java.awt.Desktop;
import javax.swing.filechooser.FileNameExtensionFilter;

public class SalesReportPanel extends JPanel {
    private JTextField startDateField;
    private JTextField endDateField;
    private JTable reportTable;
    private DefaultTableModel tableModel;
    private JLabel totalSalesLabel;
    private JLabel totalOrdersLabel;
    private JLabel averageOrderLabel;
    private SimpleDateFormat dateFormat;

    public SalesReportPanel() {
        dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupPanel();
        initComponents();
        loadDefaultData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Filter panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filters"));

        // Date fields
        startDateField = new JTextField(10);
        endDateField = new JTextField(10);

        // SET DEFAULT DATES (LAST MONTH)
        Calendar cal = Calendar.getInstance();
        endDateField.setText(dateFormat.format(cal.getTime()));
        cal.add(Calendar.MONTH, -1);
        startDateField.setText(dateFormat.format(cal.getTime()));

        startDateField.setToolTipText("Format: dd/MM/yyyy");
        endDateField.setToolTipText("Format: dd/MM/yyyy");

        filterPanel.add(new JLabel("Start Date:"));
        filterPanel.add(startDateField);
        filterPanel.add(new JLabel("End Date:"));
        filterPanel.add(endDateField);

        JButton filterButton = new JButton("Apply Filters");
        filterButton.addActionListener(e -> loadReportData());
        filterPanel.add(filterButton);

        // Statistics panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));

        totalSalesLabel = new JLabel("Total Sales: € 0.00");
        totalOrdersLabel = new JLabel("Number of Orders: 0");
        averageOrderLabel = new JLabel("Average per Order: € 0.00");

        statsPanel.add(totalSalesLabel);
        statsPanel.add(totalOrdersLabel);
        statsPanel.add(averageOrderLabel);

        // Report table
        String[] columns = {"Date", "Order ID", "Customer", "Status", "Total €"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        reportTable = new JTable(tableModel);

        // Enable column sorting with date support (column 0 is "Date")
        TableSorterUtil.enableSorting(reportTable, new int[]{0}, dateFormat);

        // Add context menu
        TableInteractionUtil.addContextMenu(reportTable,
            new TableInteractionUtil.TableAction("Order Details", this::showOrderDetails),
            null, // Separator
            new TableInteractionUtil.TableAction("Export CSV", this::exportToCSV, false)
        );

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton exportPdfButton = new JButton("Export as PDF");
        JButton exportButton = new JButton("Export CSV");
        JButton detailsButton = new JButton("Order Details");

        exportPdfButton.addActionListener(e -> exportToPDF());
        exportButton.addActionListener(e -> exportToCSV());
        detailsButton.addActionListener(e -> showOrderDetails());

        buttonPanel.add(exportPdfButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(detailsButton);

        // Main layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(filterPanel, BorderLayout.NORTH);
        topPanel.add(statsPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(reportTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadDefaultData() {
        // AUTOMATICALLY LOAD DATA WITH DEFAULT DATES
        SwingUtilities.invokeLater(() -> loadReportData());
    }

    private void loadReportData() {
        tableModel.setRowCount(0);
        try {
            String startDateText = startDateField.getText().trim();
            String endDateText = endDateField.getText().trim();

            Connection conn = DatabaseManager.getInstance().getConnection();
            String query;
            PreparedStatement pstmt = null;

            // If dates are empty, load everything
            if (startDateText.isEmpty() || endDateText.isEmpty()) {
                query = """
                    SELECT o.id, o.order_date, o.status, o.total,
                           COALESCE(c.first_name || ' ' || c.last_name, 'N/A') as customer_name
                    FROM orders o
                    LEFT JOIN customers c ON o.customer_id = c.id
                    ORDER BY o.order_date DESC
                    LIMIT 1000
                """;
                pstmt = conn.prepareStatement(query);
                System.out.println("Loading all orders (no date filter)");
            } else {
                // Try to parse dates
                Date startDate = null;
                Date endDate = null;

                try {
                    startDate = DateUtils.parseDate(startDateText, dateFormat);
                    endDate = DateUtils.parseDate(endDateText, dateFormat);
                } catch (Exception e) {
                    System.err.println("Error parsing dates: " + e.getMessage());
                }

                if (startDate == null || endDate == null) {
                    JOptionPane.showMessageDialog(this,
                        "Invalid date format. Use dd/MM/yyyy\nLoading all orders instead.",
                        "Warning", JOptionPane.WARNING_MESSAGE);

                    // Fallback: load everything
                    query = """
                        SELECT o.id, o.order_date, o.status, o.total,
                               COALESCE(c.first_name || ' ' || c.last_name, 'N/A') as customer_name
                        FROM orders o
                        LEFT JOIN customers c ON o.customer_id = c.id
                        ORDER BY o.order_date DESC
                        LIMIT 1000
                    """;
                    pstmt = conn.prepareStatement(query);
                } else {
                    // DATE FILTER WITH SIMPLE STRING APPROACH
                    query = """
                        SELECT o.id, o.order_date, o.status, o.total,
                               COALESCE(c.first_name || ' ' || c.last_name, 'N/A') as customer_name
                        FROM orders o
                        LEFT JOIN customers c ON o.customer_id = c.id
                        WHERE o.order_date >= ? AND o.order_date <= ?
                        ORDER BY o.order_date DESC
                    """;

                    pstmt = conn.prepareStatement(query);

                    // CONVERT DATES TO ISO STRING FOR COMPARISON
                    String startDateISO = String.format("%04d-%02d-%02d 00:00:00",
                        startDate.getYear() + 1900, startDate.getMonth() + 1, startDate.getDate());
                    String endDateISO = String.format("%04d-%02d-%02d 23:59:59",
                        endDate.getYear() + 1900, endDate.getMonth() + 1, endDate.getDate());

                    pstmt.setString(1, startDateISO);
                    pstmt.setString(2, endDateISO);

                    System.out.println("Filtering orders from " + startDateISO + " to " + endDateISO);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                double totalSales = 0;
                int totalOrders = 0;

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();

                    // DATE PARSING
                    Date orderDate = DateUtils.parseDate(rs, "order_date");
                    if (orderDate != null) {
                        row.add(DateUtils.formatDate(orderDate, dateFormat));
                    } else {
                        row.add("N/A");
                    }

                    row.add(rs.getInt("id"));
                    row.add(rs.getString("customer_name"));
                    row.add(rs.getString("status"));
                    double total = rs.getDouble("total");
                    row.add(String.format("%.2f", total));

                    tableModel.addRow(row);

                    totalSales += total;
                    totalOrders++;
                }

                // Update statistics
                totalSalesLabel.setText(String.format("Total Sales: € %.2f", totalSales));
                totalOrdersLabel.setText("Number of Orders: " + totalOrders);
                if (totalOrders > 0) {
                    averageOrderLabel.setText(String.format("Average per Order: € %.2f", totalSales / totalOrders));
                } else {
                    averageOrderLabel.setText("Average per Order: € 0.00");
                }

                System.out.println("Loaded " + totalOrders + " orders, total sales: €" + totalSales);
            }

            if (pstmt != null) pstmt.close();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading the report: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOrderDetails() {
        int selectedRow = reportTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = reportTable.convertRowIndexToModel(selectedRow);
            int orderId = (int)tableModel.getValueAt(modelRow, 1);

            // Create detail window
            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            JDialog detailDialog;
            if (parentWindow instanceof JFrame) {
                detailDialog = new JDialog((JFrame) parentWindow, "Order Details #" + orderId, true);
            } else {
                detailDialog = new JDialog((JDialog) parentWindow, "Order Details #" + orderId, true);
            }

            detailDialog.setSize(600, 400);
            detailDialog.setLocationRelativeTo(null);
            detailDialog.setLayout(new BorderLayout(10, 10));

            // Details table
            String[] columns = {"Product", "Quantity", "Unit Price", "Total"};
            DefaultTableModel detailModel = new DefaultTableModel(columns, 0);
            JTable detailTable = new JTable(detailModel);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = """
                    SELECT COALESCE(p.name, 'Product N/A') as product_name,
                           d.quantity, d.unit_price,
                           (d.quantity * d.unit_price) as total
                    FROM order_details d
                    LEFT JOIN products p ON d.product_id = p.id
                    WHERE d.order_id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> row = new Vector<>();
                            row.add(rs.getString("product_name"));
                            row.add(rs.getInt("quantity"));
                            row.add(String.format("%.2f", rs.getDouble("unit_price")));
                            row.add(String.format("%.2f", rs.getDouble("total")));
                            detailModel.addRow(row);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error loading the details: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            detailDialog.add(new JScrollPane(detailTable), BorderLayout.CENTER);
            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> detailDialog.dispose());
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(closeButton);
            detailDialog.add(buttonPanel, BorderLayout.SOUTH);
            detailDialog.setVisible(true);
        }
    }

    private void exportToPDF() {
        String subtitle = String.format("From: %s to: %s", startDateField.getText(), endDateField.getText());
        String fileName = String.format("sales_report_%s.pdf", new SimpleDateFormat("yyyyMMdd").format(new Date()));

        ReportPDFGenerator pdfGenerator = new ReportPDFGenerator(
            tableModel,
            "Sales Report",
            subtitle,
            fileName
        );

        pdfGenerator.generateAndSave(this);
    }

    private void exportToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Report to CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getAbsolutePath().endsWith(".csv")) {
                fileToSave = new File(fileToSave + ".csv");
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileToSave))) {
                // Column headers
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    if (i > 0) writer.print(",");
                    writer.print(tableModel.getColumnName(i));
                }
                writer.println();

                // Table data
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        if (j > 0) line.append(",");
                        String value = tableModel.getValueAt(i, j).toString();
                        if (value.contains(",")) {
                            value = "\"" + value + "\"";
                        }
                        line.append(value);
                    }
                    writer.println(line);
                }

                writer.println();
                writer.println("Summary");
                writer.println("Total Sales," + totalSalesLabel.getText().replace("Total Sales: ", ""));
                writer.println("Number of Orders," + totalOrdersLabel.getText().replace("Number of Orders: ", ""));
                writer.println("Average per Order," + averageOrderLabel.getText().replace("Average per Order: ", ""));

                JOptionPane.showMessageDialog(this,
                    "Report exported successfully",
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
