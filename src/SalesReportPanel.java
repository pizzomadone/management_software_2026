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
    private DatePickerField startDateField;
    private DatePickerField endDateField;
    private JTable reportTable;
    private DefaultTableModel tableModel;
    private JLabel totalSalesLabel;
    private JLabel totalOrdersLabel;
    private JLabel averageOrderLabel;
    private JLabel totalCostLabel;
    private JLabel grossProfitLabel;
    private JLabel historicalCostNoteLabel;
    private double summarySales, summaryCost, summaryProfit;
    private int summaryOrders;
    private boolean summaryCostsKnown;
    private SimpleDateFormat dateFormat;
    private JButton detailsButton;

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
        startDateField = new DatePickerField(10);
        endDateField = new DatePickerField(10, true);

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
        JPanel statsPanel = new JPanel(new GridLayout(1, 5, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));

        totalSalesLabel = new JLabel();
        totalOrdersLabel = new JLabel();
        averageOrderLabel = new JLabel();
        totalCostLabel = new JLabel();
        grossProfitLabel = new JLabel();
        totalCostLabel.setToolTipText(I18n.tr("Historical cost is the actual cost of the products sold, determined from the FIFO lots used when the sale was completed."));
        grossProfitLabel.setToolTipText(I18n.tr("Gross profit = historical sales revenue - historical FIFO purchase cost. Sales without reconstructable historical cost are excluded."));
        historicalCostNoteLabel = new JLabel();
        historicalCostNoteLabel.setFont(historicalCostNoteLabel.getFont().deriveFont(
            Math.max(10f, historicalCostNoteLabel.getFont().getSize2D() - 1f)));
        updateStatistics(0, 0, 0, 0, true);

        statsPanel.add(totalSalesLabel);
        statsPanel.add(totalOrdersLabel);
        statsPanel.add(averageOrderLabel);
        statsPanel.add(totalCostLabel);
        statsPanel.add(grossProfitLabel);

        // Report table
        String[] columns = {"Date", "Order ID", "Customer", "Status",
            "Sales", "Historical Cost", "Gross Profit"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        reportTable = new JTable(tableModel);
        TableStyleUtil.applyCompact(reportTable);
        installCostHeaders(reportTable, 5, 6);

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
        detailsButton = new JButton("Order Details");

        exportPdfButton.addActionListener(e -> exportToPDF());
        exportButton.addActionListener(e -> exportToCSV());
        detailsButton.addActionListener(e -> showOrderDetails());

        // Initially disable the details button (no row selected)
        detailsButton.setEnabled(false);

        // Add selection listener to enable/disable the details button
        reportTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                detailsButton.setEnabled(reportTable.getSelectedRow() != -1);
            }
        });

        buttonPanel.add(exportPdfButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(detailsButton);

        // Main layout
        JPanel statsContainer = new JPanel(new BorderLayout(0, 4));
        statsContainer.add(statsPanel, BorderLayout.CENTER);
        statsContainer.add(historicalCostNoteLabel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(filterPanel, BorderLayout.NORTH);
        topPanel.add(statsContainer, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(reportTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadDefaultData() {
        // AUTOMATICALLY LOAD DATA WITH DEFAULT DATES
        SwingUtilities.invokeLater(() -> loadReportData());
    }

    private boolean reportValid;

    private void loadReportData() {
        reportValid=false;
        tableModel.setRowCount(0);
        java.util.List<java.util.Vector<Object>> fetchedRows=new java.util.ArrayList<>();
        updateStatistics(0,0,0,0,true);
        try {
            Date start = DateUtils.parseDate(startDateField.getText(),dateFormat);
            Date end = DateUtils.parseDate(endDateField.getText(),dateFormat);
            if (start!=null && end!=null && start.after(end)) throw new IllegalArgumentException("Start date must not follow end date");
            Connection conn = DatabaseManager.getInstance().getConnection();
            // Restrict the expensive per-line FIFO cost calculation to the
            // requested sales window. The previous cost CTE aggregated all
            // historical order_details even when only one month was visible.
            String query = """
                WITH filtered_orders AS MATERIALIZED (
                    SELECT id FROM orders
                    WHERE status='Completed'
                """;
            if (start!=null)
                query += " AND warestat_date(COALESCE(completed_date,order_date))>=?";
            if (end!=null)
                query += " AND warestat_date(COALESCE(completed_date,order_date))<=?";
            query += """
                ), cg AS (
                    SELECT d.order_id,SUM(%s) AS cost_total,
                           SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS missing_cost
                    FROM filtered_orders filtered
                    JOIN order_details d ON d.order_id=filtered.id
                    GROUP BY d.order_id
                )
                SELECT o.id,COALESCE(o.completed_date,o.order_date) AS sale_date,
                       o.status,o.total_ex_vat AS total,
                       COALESCE(c.first_name || ' ' || c.last_name,'N/A') AS customer_name,
                       cg.cost_total,cg.missing_cost
                FROM filtered_orders filtered
                JOIN orders o ON o.id=filtered.id
                LEFT JOIN customers c ON c.id=o.customer_id
                LEFT JOIN cg ON cg.order_id=o.id
                ORDER BY warestat_date(COALESCE(o.completed_date,o.order_date)) DESC,o.id DESC
                """.formatted(CostIntegrity.historicalCostSql("d"),
                               CostIntegrity.historicalCostSql("d"));
            PreparedStatement pstmt = conn.prepareStatement(query);
            int parameter=1;
            SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd");
            if (start!=null) pstmt.setString(parameter++,iso.format(start));
            if (end!=null) pstmt.setString(parameter++,iso.format(end));

            try (ResultSet rs = pstmt.executeQuery()) {
                double totalSales = 0;
                double totalCost = 0;
                double totalGrossProfit = 0;
                boolean allCostsKnown = true;
                int totalOrders = 0;

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();

                    // DATE PARSING
                    Date saleDate = DateUtils.parseDate(rs, "sale_date");
                    if (saleDate != null) {
                        row.add(DateUtils.formatDate(saleDate, dateFormat));
                    } else {
                        row.add("N/A");
                    }

                    row.add(rs.getInt("id"));
                    row.add(rs.getString("customer_name"));
                    row.add(rs.getString("status"));
                    double total = rs.getDouble("total");
                    row.add(Numbers.moneyText(total));

                    int missingCost = rs.getInt("missing_cost");
                    boolean hasCostRows = rs.getObject("missing_cost") != null;
                    boolean costKnown = hasCostRows && missingCost == 0;
                    if (costKnown) {
                        double cost = Numbers.money(java.math.BigDecimal.valueOf(rs.getDouble("cost_total")));
                        double profit = Numbers.money(java.math.BigDecimal.valueOf(total - cost));
                        row.add(Numbers.moneyText(cost));
                        row.add(Numbers.moneyText(profit));
                        totalCost = Numbers.add(totalCost,cost);
                        totalGrossProfit = Numbers.add(totalGrossProfit,profit);
                    } else {
                        row.add(I18n.tr("Historical cost unavailable"));
                        row.add(I18n.tr("Historical cost unavailable"));
                        allCostsKnown = false;
                    }

                    fetchedRows.add(row);

                    totalSales = Numbers.add(totalSales,total);
                    totalOrders++;
                }

                TableStyleUtil.appendRows(tableModel,fetchedRows);
                // Update statistics
                updateStatistics(totalSales,totalOrders,totalCost,totalGrossProfit,allCostsKnown);
                reportValid=true;

            }

            if (pstmt != null) pstmt.close();

        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading the report: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatistics(double totalSales,int totalOrders,double totalCost,
                                  double grossProfit,boolean allCostsKnown) {
        summarySales=totalSales;summaryCost=totalCost;summaryProfit=grossProfit;
        summaryOrders=totalOrders;summaryCostsKnown=allCostsKnown;
        double averageOrder = totalOrders > 0 ? totalSales / totalOrders : 0;
        totalSalesLabel.setText("Total Sales: " + SettingsPanel.formatCurrency(totalSales));
        totalOrdersLabel.setText("Number of Orders: " + totalOrders);
        averageOrderLabel.setText("Average per Order: " + SettingsPanel.formatCurrency(averageOrder));
        String marker = allCostsKnown ? "" : " *";
        totalCostLabel.setText("<html><u>" + I18n.tr("Historical Cost") + "</u>: " +
            SettingsPanel.formatCurrency(totalCost) + marker + "</html>");
        grossProfitLabel.setText(I18n.tr("Gross Profit") + ": " +
            SettingsPanel.formatCurrency(grossProfit) + marker);
        historicalCostNoteLabel.setText(allCostsKnown ? "" :
            I18n.tr("* Sales without reconstructable historical cost are excluded from historical cost and gross profit calculations."));

        I18n.localize(totalSalesLabel);
        I18n.localize(totalOrdersLabel);
        I18n.localize(averageOrderLabel);
        I18n.localize(totalCostLabel);
        I18n.localize(grossProfitLabel);
    }

    private static void installCostHeaders(JTable target, int costColumn, int profitColumn) {
        javax.swing.table.TableCellRenderer delegate = target.getTableHeader().getDefaultRenderer();
        target.getTableHeader().setDefaultRenderer((table, value, selected, focus, row, column) -> {
            Component component = delegate.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (component instanceof JLabel label) {
                int modelColumn = target.convertColumnIndexToModel(column);
                label.setToolTipText(null);
                if (modelColumn == costColumn || modelColumn == profitColumn) {
                    label.setText("<html><u>" + I18n.tr(modelColumn == costColumn ? "Historical Cost" : "Gross Profit") + "</u></html>");
                    label.setToolTipText(I18n.tr(modelColumn == costColumn
                        ? "Historical cost is the actual cost of the products sold, determined from the FIFO lots used when the sale was completed."
                        : "Gross profit = historical sales revenue - historical FIFO purchase cost. Sales without reconstructable historical cost are excluded."));
                }
            }
            return component;
        });
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
            String[] columns = {"Product", "Quantity", "Unit Price excl. VAT", "Total excl. VAT", "Historical Cost", "Gross Profit"};
            DefaultTableModel detailModel = new DefaultTableModel(columns, 0);
            JTable detailTable = new JTable(detailModel);
            TableStyleUtil.applyCompact(detailTable);
            installCostHeaders(detailTable, 4, 5);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = """
                    SELECT COALESCE(p.name, 'Product N/A') as product_name,
                           d.quantity, d.sale_unit_price_ex_vat,
                           %s AS effective_cost,
                           warestat_line_total(d.quantity,d.sale_unit_price_ex_vat) as total
                    FROM order_details d
                    LEFT JOIN products p ON d.product_id = p.id
                    WHERE d.order_id = ?
                """.formatted(CostIntegrity.historicalCostSql("d"));
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, orderId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Vector<Object> row = new Vector<>();
                            row.add(rs.getString("product_name"));
                            row.add(rs.getInt("quantity"));
                            row.add(Numbers.unitText(rs.getDouble("sale_unit_price_ex_vat")));
                            double lineTotal=rs.getDouble("total");
                            row.add(Numbers.moneyText(lineTotal));
                            Object historicalCost=rs.getObject("effective_cost");
                            if(historicalCost==null){
                                row.add(I18n.tr("Historical cost unavailable"));row.add(I18n.tr("Historical cost unavailable"));
                            }else{
                                double cost=rs.getDouble("effective_cost");
                                row.add(Numbers.moneyText(cost));
                                row.add(Numbers.moneyText(Numbers.money(
                                    java.math.BigDecimal.valueOf(lineTotal-cost))));
                            }
                            detailModel.addRow(row);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error loading the details: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            detailDialog.add(new JScrollPane(detailTable), BorderLayout.CENTER);
            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> detailDialog.dispose());
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(closeButton);
            detailDialog.add(buttonPanel, BorderLayout.SOUTH);
            I18n.localize(detailDialog);
            Dialogs.centerWindow(detailDialog);
            detailDialog.setVisible(true);
        }
    }

    private void exportToPDF() {
        loadReportData();if(!reportValid)return;
        String subtitle = I18n.tr("From") + ": " + startDateField.getText() + " " + I18n.tr("to") + ": " + endDateField.getText();
        String fileName = String.format("sales_report_%s.pdf", new SimpleDateFormat("yyyyMMdd").format(new Date()));

        ReportPDFGenerator pdfGenerator = new ReportPDFGenerator(
            tableModel,
            "Sales Report - Completed Orders",
            subtitle,
            fileName
        );

        pdfGenerator.generateAndSave(this);
    }

    private void exportToCSV() {
        loadReportData();if(!reportValid)return;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Report to CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getAbsolutePath().endsWith(".csv")) {
                fileToSave = new File(fileToSave + ".csv");
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileToSave, java.nio.charset.StandardCharsets.UTF_8))) {
                // Column headers
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    if (i > 0) writer.print(",");
                    writer.print(Csv.cell(tableModel.getColumnName(i)));
                }
                writer.println();

                // Table data
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        if (j > 0) line.append(",");
                        String value = Csv.cell(tableModel.getValueAt(i, j));
                        line.append(value);
                    }
                    writer.println(line);
                }

                writer.println();
                writer.println(Csv.cell(I18n.tr("Summary")));
                writer.println(Csv.cell(I18n.tr("Total Sales"))+","+Numbers.moneyText(summarySales));
                writer.println(Csv.cell(I18n.tr("Number of Orders"))+","+summaryOrders);
                writer.println(Csv.cell(I18n.tr("Average per Order"))+","+Numbers.moneyText(summaryOrders>0 ? summarySales/summaryOrders : 0));
                String marker=summaryCostsKnown ? "" : " *";
                writer.println(Csv.cell(I18n.tr("Historical Cost"))+","+Csv.cell(Numbers.moneyText(summaryCost)+marker));
                writer.println(Csv.cell(I18n.tr("Gross Profit"))+","+Csv.cell(Numbers.moneyText(summaryProfit)+marker));
                if (!summaryCostsKnown) writer.println(Csv.cell(I18n.tr(
                    "* Sales without reconstructable historical cost are excluded from historical cost and gross profit calculations.")));

                Csv.finish(writer);
                Dialogs.showMessageDialog(this,
                    "Report exported successfully",
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error during export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    public void refreshData() { loadReportData(); }
}

