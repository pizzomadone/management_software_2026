import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalDate;
import java.time.YearMonth;

public class AdvancedStatsPanel extends JPanel {
    private static final int MAX_PRODUCT_SERIES = 5;
    private static final Color TOTAL_SALES_COLOR = new Color(45, 105, 190);
    private static final Color[] PRODUCT_SERIES_COLORS = {
        new Color(220, 80, 70),
        new Color(55, 155, 95),
        new Color(145, 90, 190),
        new Color(225, 145, 45),
        new Color(45, 165, 180)
    };

    private JTabbedPane tabbedPane;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat filterDateFormat;
    private JPanel salesChartPanel;
    private JPanel productsChartPanel;
    private JTable topProductsTable;
    private JComboBox<String> periodCombo;
    private JLabel fromDateLabel;
    private JLabel toDateLabel;
    private DatePickerField fromDateField;
    private DatePickerField toDateField;
    private JButton applyPeriodButton;
    private JButton addProductSeriesButton;
    private JPanel selectedSeriesPanel;
    private Map<String, double[]> monthlySales;
    private final Map<Integer, String> selectedSalesProducts = new LinkedHashMap<>();
    private Map<Integer, Map<String, Double>> productMonthlySales = new LinkedHashMap<>();
    private List<Object[]> productStats;
    private double productsTotalRevenue;

    private record DateRange(LocalDate start, LocalDate end) {}

    public AdvancedStatsPanel() {
        dateFormat = new SimpleDateFormat("MM/yyyy");
        filterDateFormat = new SimpleDateFormat("dd/MM/yyyy");
        filterDateFormat.setLenient(false);
        monthlySales = new HashMap<>();
        productStats = new ArrayList<>();

        setupPanel();
        initComponents();
        loadData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        tabbedPane = new JTabbedPane();

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        // Shared period filter: it applies to both statistics tabs.
        JPanel periodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        periodCombo = new JComboBox<>(new String[]{
            "Last 6 months", "Last year", "Last 2 years", "Custom range"
        });
        periodCombo.putClientProperty("i18n.values", true);

        LocalDate today = LocalDate.now();
        fromDateField = new DatePickerField(10);
        toDateField = new DatePickerField(10);
        fromDateField.setText(DateUtils.formatDate(
            java.sql.Date.valueOf(today.minusMonths(5).withDayOfMonth(1)), filterDateFormat));
        toDateField.setText(DateUtils.formatDate(java.sql.Date.valueOf(today), filterDateFormat));

        fromDateLabel = new JLabel("From:");
        toDateLabel = new JLabel("To:");
        applyPeriodButton = new JButton("Apply");
        applyPeriodButton.addActionListener(e -> loadData());

        periodPanel.add(new JLabel("Period:"));
        periodPanel.add(periodCombo);
        periodPanel.add(fromDateLabel);
        periodPanel.add(fromDateField);
        periodPanel.add(toDateLabel);
        periodPanel.add(toDateField);
        periodPanel.add(applyPeriodButton);

        periodCombo.addActionListener(e -> {
            updateCustomRangeVisibility();
            if (periodCombo.getSelectedIndex() != 3) loadData();
        });
        controls.add(periodPanel);

        updateCustomRangeVisibility();

        // Sales tab
        JPanel salesPanel = new JPanel(new BorderLayout(0, 4));

        // Product-series controls belong only to Sales Trend.
        JPanel productSeriesBar = new JPanel(new BorderLayout(8, 0));
        productSeriesBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel productSeriesActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        productSeriesActions.setOpaque(false);
        productSeriesActions.add(new JLabel("Product sales:"));
        addProductSeriesButton = new JButton("+");
        addProductSeriesButton.setToolTipText(I18n.tr("Add product sales line"));
        addProductSeriesButton.setMargin(new Insets(1, 9, 1, 9));
        addProductSeriesButton.addActionListener(e -> showAddProductSeriesDialog());
        productSeriesActions.add(addProductSeriesButton);
        productSeriesBar.add(productSeriesActions, BorderLayout.WEST);

        selectedSeriesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selectedSeriesPanel.setOpaque(false);
        JScrollPane selectedSeriesScroll = new JScrollPane(
            selectedSeriesPanel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        selectedSeriesScroll.setBorder(null);
        selectedSeriesScroll.setOpaque(false);
        selectedSeriesScroll.getViewport().setOpaque(false);
        selectedSeriesScroll.setPreferredSize(new Dimension(560, 34));
        productSeriesBar.add(selectedSeriesScroll, BorderLayout.CENTER);

        refreshSelectedSeriesPanel();

        salesChartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSalesChart(g);
            }
        };
        salesPanel.add(productSeriesBar, BorderLayout.NORTH);
        salesPanel.add(salesChartPanel, BorderLayout.CENTER);

        // Products tab
        JPanel productsPanel = new JPanel(new BorderLayout());
        productsChartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawProductsChart(g);
            }
        };
        productsChartPanel.setMinimumSize(new Dimension(0, 180));

        String[] columns = {"Product", "Quantity Sold", "Revenue", "% of Total"};
        DefaultTableModel initialModel =
            new DefaultTableModel(new Object[0][4], columns) {
                @Override public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        topProductsTable = new JTable(initialModel);
        topProductsTable.setDefaultEditor(Object.class, null);
        TableSorterUtil.enableSorting(topProductsTable);

        JScrollPane productTableScroll = new JScrollPane(topProductsTable);
        productTableScroll.setMinimumSize(new Dimension(0, 120));

        JSplitPane productsSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, productsChartPanel, productTableScroll);
        productsSplit.setBorder(null);
        productsSplit.setDividerSize(5);
        productsSplit.setResizeWeight(0.55);
        productsSplit.setContinuousLayout(true);
        SwingUtilities.invokeLater(() -> productsSplit.setDividerLocation(0.55));

        productsPanel.add(productsSplit, BorderLayout.CENTER);

        tabbedPane.addTab("Sales Trend", salesPanel);
        tabbedPane.addTab("Product Analysis", productsPanel);

        add(controls, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void updateCustomRangeVisibility() {
        boolean custom = periodCombo != null && periodCombo.getSelectedIndex() == 3;
        fromDateLabel.setVisible(custom);
        fromDateField.setVisible(custom);
        toDateLabel.setVisible(custom);
        toDateField.setVisible(custom);
        applyPeriodButton.setVisible(custom);

        Container parent = periodCombo.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private DateRange selectedDateRange() throws Exception {
        LocalDate today = LocalDate.now();
        return switch (periodCombo.getSelectedIndex()) {
            case 0 -> new DateRange(
                YearMonth.from(today).minusMonths(5).atDay(1), today);
            case 1 -> new DateRange(
                YearMonth.from(today).minusMonths(11).atDay(1), today);
            case 2 -> new DateRange(
                YearMonth.from(today).minusMonths(23).atDay(1), today);
            case 3 -> {
                Date from = DateUtils.parseDate(fromDateField.getText().trim(), filterDateFormat);
                Date to = DateUtils.parseDate(toDateField.getText().trim(), filterDateFormat);
                if (from == null || to == null)
                    throw new IllegalArgumentException(I18n.tr("Enter a valid date range"));
                LocalDate start = new java.sql.Date(from.getTime()).toLocalDate();
                LocalDate end = new java.sql.Date(to.getTime()).toLocalDate();
                if (start.isAfter(end))
                    throw new IllegalArgumentException(
                        I18n.tr("Start date must not be after end date"));
                yield new DateRange(start, end);
            }
            default -> new DateRange(
                YearMonth.from(today).minusMonths(11).atDay(1), today);
        };
    }

    private void loadData() {
        try {
            DateRange range = selectedDateRange();
            loadSalesData(range);
            loadProductsData(range);
            repaint();
        } catch (Exception e) {
            Dialogs.showMessageDialog(this,
                I18n.tr(e.getMessage() == null ? "Unable to load statistics" : e.getMessage()),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> monthsInRange(DateRange range) {
        List<String> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(range.start());
        YearMonth last = YearMonth.from(range.end());
        while (!cursor.isAfter(last)) {
            months.add(cursor.toString());
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private void loadSalesData(DateRange range) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String normalizedDate = DateUtils.sqlDate("o.order_date");
        String query =
            "SELECT strftime('%Y-%m', " + normalizedDate + ") AS month, " +
            "SUM(o.total) AS total, COUNT(*) AS num_orders " +
            "FROM orders o " +
            "WHERE o.order_date IS NOT NULL AND o.status='Completed' " +
            "AND " + normalizedDate + " BETWEEN ? AND ? " +
            "GROUP BY month ORDER BY month";

        Map<String, double[]> newMonthlySales = new LinkedHashMap<>();
        List<String> months = monthsInRange(range);
        for (String month : months)
            newMonthlySales.put(month, new double[]{0.0, 0.0});

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, range.start().toString());
            stmt.setString(2, range.end().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String month = rs.getString("month");
                    if (month == null || month.isBlank()) continue;
                    double total = Numbers.money(
                        java.math.BigDecimal.valueOf(rs.getDouble("total")));
                    int numOrders = rs.getInt("num_orders");
                    newMonthlySales.put(month, new double[]{total, numOrders});
                }
            }
        }

        monthlySales = newMonthlySales;
        loadSelectedProductSales(conn, range, months);
    }

    private void loadSelectedProductSales(Connection conn, DateRange range,
            List<String> months) throws SQLException {
        Map<Integer, Map<String, Double>> result = new LinkedHashMap<>();
        if (selectedSalesProducts.isEmpty()) {
            productMonthlySales = result;
            return;
        }

        String normalizedDate = DateUtils.sqlDate("o.order_date");
        String query =
            "SELECT strftime('%Y-%m', " + normalizedDate + ") AS month, " +
            "SUM(warestat_line_total(d.quantity,d.unit_price)) AS revenue " +
            "FROM order_details d " +
            "JOIN orders o ON o.id=d.order_id " +
            "WHERE d.product_id=? AND o.status='Completed' " +
            "AND o.order_date IS NOT NULL " +
            "AND " + normalizedDate + " BETWEEN ? AND ? " +
            "GROUP BY month ORDER BY month";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            for (Integer productId : selectedSalesProducts.keySet()) {
                Map<String, Double> series = new LinkedHashMap<>();
                for (String month : months) series.put(month, 0.0);

                stmt.setInt(1, productId);
                stmt.setString(2, range.start().toString());
                stmt.setString(3, range.end().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String month = rs.getString("month");
                        if (month == null || month.isBlank()) continue;
                        series.put(month, Numbers.money(
                            java.math.BigDecimal.valueOf(rs.getDouble("revenue"))));
                    }
                }
                result.put(productId, series);
            }
        }
        productMonthlySales = result;
    }

    private void drawSalesChart(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            int width = salesChartPanel.getWidth();
            int height = salesChartPanel.getHeight();
            int leftPadding = 78;
            int rightPadding = 30;
            int topPadding = selectedSalesProducts.isEmpty() ? 35 : 62;
            int bottomPadding = 72;

            Map<String, double[]> currentSales = monthlySales;
            if (currentSales.isEmpty()) {
                drawCenteredMessage(g2d, width, height,
                    I18n.tr("No sales data available"));
                return;
            }

            List<String> months = new ArrayList<>(currentSales.keySet());
            Collections.sort(months);
            if (months.isEmpty()) return;

            double maxSale = currentSales.values().stream()
                .mapToDouble(values -> values[0]).max().orElse(0);
            for (Map<String, Double> series : productMonthlySales.values()) {
                maxSale = Math.max(maxSale,
                    series.values().stream().mapToDouble(Double::doubleValue)
                        .max().orElse(0));
            }

            int chartWidth = Math.max(1, width - leftPadding - rightPadding);
            int chartHeight = Math.max(1, height - topPadding - bottomPadding);

            g2d.setColor(Color.BLACK);
            g2d.drawLine(leftPadding, topPadding,
                leftPadding, topPadding + chartHeight);
            g2d.drawLine(leftPadding, topPadding + chartHeight,
                leftPadding + chartWidth, topPadding + chartHeight);

            double step = months.size() <= 1
                ? 0.0 : chartWidth / (double)(months.size() - 1);

            if (maxSale <= 0) {
                g2d.drawString(I18n.tr("No sales in this period"),
                    leftPadding + 10, topPadding + chartHeight / 2);
            } else {
                drawSalesSeries(g2d, months, currentSales, null,
                    TOTAL_SALES_COLOR, leftPadding, topPadding,
                    chartHeight, step, maxSale, 4);

                int colorIndex = 0;
                for (Integer productId : selectedSalesProducts.keySet()) {
                    Map<String, Double> series = productMonthlySales.get(productId);
                    if (series == null) continue;
                    drawSalesSeries(g2d, months, null, series,
                        PRODUCT_SERIES_COLORS[colorIndex % PRODUCT_SERIES_COLORS.length],
                        leftPadding, topPadding, chartHeight, step, maxSale, 3);
                    colorIndex++;
                }
            }

            drawSalesLegend(g2d, width);

            int maxLabels = Math.max(2, chartWidth / 85);
            int labelEvery = Math.max(1,
                (int)Math.ceil(months.size() / (double)maxLabels));
            g2d.setColor(Color.BLACK);
            for (int i = 0; i < months.size(); i++) {
                if (i % labelEvery != 0 && i != months.size() - 1) continue;
                String month = months.get(i);
                String label = month.length() >= 7
                    ? month.substring(5, 7) + "/" + month.substring(0, 4)
                    : month;
                int x = leftPadding + (int)Math.round(i * step);
                Graphics2D labels = (Graphics2D)g2d.create();
                try {
                    labels.rotate(Math.PI / 4, x, topPadding + chartHeight + 18);
                    labels.drawString(label, x - 12, topPadding + chartHeight + 18);
                } finally {
                    labels.dispose();
                }
            }

            if (maxSale > 0) {
                g2d.setColor(Color.BLACK);
                for (int i = 0; i <= 5; i++) {
                    int y = topPadding + chartHeight - (i * chartHeight / 5);
                    double value = (maxSale * i) / 5.0;
                    String label = SettingsPanel.formatCurrency(value);
                    int textWidth = g2d.getFontMetrics().stringWidth(label);
                    g2d.drawString(label,
                        Math.max(2, leftPadding - textWidth - 6), y + 5);
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private void drawSalesSeries(Graphics2D g2d, List<String> months,
            Map<String, double[]> totalSeries, Map<String, Double> productSeries,
            Color color, int leftPadding, int topPadding, int chartHeight,
            double step, double maxSale, int pointSize) {
        Point previous = null;
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(productSeries == null ? 2.4f : 2.0f));

        for (int i = 0; i < months.size(); i++) {
            String month = months.get(i);
            double sale = productSeries == null
                ? totalSeries.getOrDefault(month, new double[]{0.0, 0.0})[0]
                : productSeries.getOrDefault(month, 0.0);

            int x = leftPadding + (int)Math.round(i * step);
            int y = topPadding + chartHeight -
                (int)Math.round((sale / maxSale) * chartHeight);

            int diameter = pointSize * 2;
            g2d.fillOval(x - pointSize, y - pointSize, diameter, diameter);
            if (previous != null) g2d.drawLine(previous.x, previous.y, x, y);
            previous = new Point(x, y);
        }
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawSalesLegend(Graphics2D g2d, int width) {
        int x = 82;
        int y = 20;

        x = drawLegendItem(g2d, x, y, TOTAL_SALES_COLOR,
            I18n.tr("Total sales"), width);

        int colorIndex = 0;
        for (String name : selectedSalesProducts.values()) {
            if (x > width - 170) {
                x = 82;
                y += 22;
            }
            x = drawLegendItem(g2d, x, y,
                PRODUCT_SERIES_COLORS[colorIndex % PRODUCT_SERIES_COLORS.length],
                name, width);
            colorIndex++;
        }
    }

    private int drawLegendItem(Graphics2D g2d, int x, int y, Color color,
            String label, int width) {
        g2d.setColor(color);
        g2d.fillRect(x, y - 8, 18, 4);
        g2d.setColor(Color.BLACK);
        String fitted = fitText(g2d, label, Math.min(190, Math.max(40, width - x - 35)));
        g2d.drawString(fitted, x + 24, y);
        return x + 24 + g2d.getFontMetrics().stringWidth(fitted) + 20;
    }

    private void refreshSelectedSeriesPanel() {
        if (selectedSeriesPanel == null) return;
        selectedSeriesPanel.removeAll();

        int index = 0;
        for (Map.Entry<Integer, String> entry : selectedSalesProducts.entrySet()) {
            int productId = entry.getKey();
            String name = entry.getValue();

            String chipName = name.length() > 24
                ? name.substring(0, 21).stripTrailing() + "..."
                : name;
            JButton chip = new JButton(chipName + "  ×");
            chip.setToolTipText(I18n.tr("Remove product sales line") + ": " + name);
            chip.setMargin(new Insets(1, 7, 1, 7));
            chip.setFocusable(false);
            final int id = productId;
            chip.addActionListener(e -> {
                selectedSalesProducts.remove(id);
                productMonthlySales.remove(id);
                refreshSelectedSeriesPanel();
                loadData();
            });
            selectedSeriesPanel.add(chip);
            index++;
        }

        addProductSeriesButton.setEnabled(selectedSalesProducts.size() < MAX_PRODUCT_SERIES);
        selectedSeriesPanel.revalidate();
        selectedSeriesPanel.repaint();
    }

    private void showAddProductSeriesDialog() {
        if (selectedSalesProducts.size() >= MAX_PRODUCT_SERIES) {
            Dialogs.showMessageDialog(this,
                I18n.tr("You can add up to five product lines to the chart"),
                I18n.tr("Product sales"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(
            owner, I18n.tr("Add product to sales chart"),
            Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        searchPanel.add(new JLabel(I18n.tr("Search:")));
        JTextField search = new JTextField(24);
        searchPanel.add(TableInteractionUtil.withClearButton(
            search, () -> loadProductsForSalesChart(null, "")));

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Code", "Product"}, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(330);
        TableSorterUtil.enableSorting(table);

        Runnable reload = () -> loadProductsForSalesChart(model, search.getText().trim());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { reload.run(); }
            @Override public void removeUpdate(DocumentEvent e) { reload.run(); }
            @Override public void changedUpdate(DocumentEvent e) { reload.run(); }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 330));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton(I18n.tr("Add"));
        JButton cancel = new JButton(I18n.tr("Cancel"));
        buttons.add(add);
        buttons.add(cancel);

        Runnable choose = () -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                Dialogs.showMessageDialog(dialog,
                    I18n.tr("Select a product to add"),
                    I18n.tr("No Selection"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            int row = table.convertRowIndexToModel(viewRow);
            int productId = ((Number)model.getValueAt(row, 0)).intValue();
            String name = String.valueOf(model.getValueAt(row, 2));

            if (selectedSalesProducts.containsKey(productId)) {
                Dialogs.showMessageDialog(dialog,
                    I18n.tr("This product is already on the chart"),
                    I18n.tr("Product sales"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            selectedSalesProducts.put(productId, name);
            refreshSelectedSeriesPanel();
            dialog.dispose();
            loadData();
        };

        add.addActionListener(e -> choose.run());
        cancel.addActionListener(e -> dialog.dispose());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e))
                    choose.run();
            }
        });

        dialog.add(searchPanel, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setResizable(true);
        Dialogs.centerWindow(dialog);

        loadProductsForSalesChart(model, "");
        dialog.setVisible(true);
    }

    private void loadProductsForSalesChart(DefaultTableModel model, String term) {
        if (model == null) return;
        model.setRowCount(0);
        String normalized = term == null ? "" : term.trim();
        String pattern = "%" + normalized + "%";

        String query =
            "SELECT id,code,name FROM products " +
            "WHERE (?='' OR LOWER(code) LIKE LOWER(?) OR LOWER(name) LIKE LOWER(?)) " +
            "ORDER BY name LIMIT 500";

        try (PreparedStatement p = DatabaseManager.getInstance().getConnection()
                .prepareStatement(query)) {
            p.setString(1, normalized);
            p.setString(2, pattern);
            p.setString(3, pattern);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    model.addRow(new Object[]{
                        r.getInt("id"), r.getString("code"), r.getString("name")
                    });
                }
            }
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Unable to load products") + ": " + e.getMessage(),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProductsData(DateRange range) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();

        String normalizedDate = DateUtils.sqlDate("o.order_date");
        String query =
            "SELECT COALESCE(p.name, 'Product N/A') AS name, " +
            "SUM(d.quantity) AS total_quantity, " +
            "SUM(warestat_line_total(d.quantity,d.unit_price)) AS revenue, " +
            "COUNT(DISTINCT o.id) AS num_orders " +
            "FROM order_details d " +
            "LEFT JOIN products p ON d.product_id = p.id " +
            "LEFT JOIN orders o ON d.order_id = o.id " +
            "WHERE o.order_date IS NOT NULL AND o.status='Completed' " +
            "AND " + normalizedDate + " BETWEEN ? AND ? " +
            "GROUP BY d.product_id, p.name " +
            "ORDER BY revenue DESC";

        List<Object[]> newStats = new ArrayList<>();
        double totalRevenue = 0;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, range.start().toString());
            stmt.setString(2, range.end().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    long quantity = rs.getLong("total_quantity");
                    double revenue = Numbers.money(
                        java.math.BigDecimal.valueOf(rs.getDouble("revenue")));
                    totalRevenue = Numbers.add(totalRevenue, revenue);
                    if (newStats.size() < 10)
                        newStats.add(new Object[]{name, quantity, revenue});
                }
            }
        }

        productStats = newStats;
        productsTotalRevenue = totalRevenue;

        Object[][] data = new Object[productStats.size()][4];
        for (int i = 0; i < productStats.size(); i++) {
            Object[] row = productStats.get(i);
            data[i][0] = row[0];
            data[i][1] = row[1];
            data[i][2] = SettingsPanel.formatCurrency((double)row[2]);
            data[i][3] = totalRevenue > 0
                ? String.format("%.1f%%", ((double)row[2] / totalRevenue) * 100)
                : "0.0%";
        }

        DefaultTableModel model =
            new DefaultTableModel(
                data, new String[]{"Product", "Quantity Sold", "Revenue", "% of Total"}) {
                @Override public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        topProductsTable.setModel(model);
        topProductsTable.setDefaultEditor(Object.class, null);
        TableSorterUtil.enableSorting(topProductsTable);
        I18n.localize(topProductsTable);
    }

    private void drawProductsChart(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            int width = productsChartPanel.getWidth();
            int height = productsChartPanel.getHeight();

            if (productStats.isEmpty()) {
                drawCenteredMessage(g2d, width, height,
                    I18n.tr("No product data available"));
                return;
            }

            double totalRevenue = productsTotalRevenue;
            if (totalRevenue <= 0) {
                drawCenteredMessage(g2d, width, height,
                    I18n.tr("No revenue data"));
                return;
            }

            Color[] colors = {
                new Color(255, 99, 132), new Color(54, 162, 235),
                new Color(255, 205, 86), new Color(75, 192, 192),
                new Color(153, 102, 255), new Color(255, 159, 64),
                new Color(201, 203, 207), new Color(255, 99, 255),
                new Color(99, 255, 132), new Color(132, 99, 255)
            };

            double topRevenue = productStats.stream()
                .mapToDouble(row -> (double)row[2]).sum();
            double otherRevenue = Math.max(0, totalRevenue - topRevenue);
            boolean hasOther = otherRevenue > 0.005;
            int legendItems = productStats.size() + (hasOther ? 1 : 0);

            int legendWidth = width >= 650
                ? Math.min(380, Math.max(280, width / 3))
                : 0;
            int pieAreaWidth = width - legendWidth;
            int padding = 20;
            int diameter = Math.max(40,
                Math.min(pieAreaWidth - 2 * padding, height - 2 * padding));
            int centerX = pieAreaWidth / 2;
            int centerY = height / 2;

            double startAngle = 0;
            for (int i = 0; i < productStats.size(); i++) {
                Object[] row = productStats.get(i);
                double revenue = (double)row[2];
                double percentage = revenue / totalRevenue;
                double arcAngle = 360.0 * percentage;

                Color sliceColor = colors[i % colors.length];
                g2d.setColor(sliceColor);
                g2d.fillArc(centerX - diameter / 2, centerY - diameter / 2,
                    diameter, diameter,
                    (int)Math.round(startAngle), (int)Math.ceil(arcAngle));

                drawPiePercentage(g2d, centerX, centerY, diameter,
                    startAngle, arcAngle, percentage, sliceColor);
                startAngle += arcAngle;
            }

            if (hasOther) {
                Color otherColor = Color.LIGHT_GRAY;
                g2d.setColor(otherColor);
                double otherPercentage = otherRevenue / totalRevenue;
                double otherAngle = 360.0 * otherPercentage;
                g2d.fillArc(centerX - diameter / 2, centerY - diameter / 2,
                    diameter, diameter,
                    (int)Math.round(startAngle), (int)Math.ceil(otherAngle));
                drawPiePercentage(g2d, centerX, centerY, diameter,
                    startAngle, otherAngle, otherPercentage, otherColor);
            }

            if (legendWidth <= 0 || legendItems <= 0) return;

            int legendX = pieAreaWidth + 12;
            int lineHeight = Math.max(16,
                Math.min(24, Math.max(1, (height - 16) / legendItems)));
            int totalLegendHeight = lineHeight * legendItems;
            int legendY = Math.max(8, (height - totalLegendHeight) / 2);
            int swatch = Math.min(15, lineHeight - 3);
            int textX = legendX + swatch + 8;
            int maxTextWidth = Math.max(40, width - textX - 10);

            Font original = g2d.getFont();
            if (lineHeight < 19)
                g2d.setFont(original.deriveFont(
                    Math.max(10f, original.getSize2D() - 1f)));

            for (int i = 0; i < productStats.size(); i++) {
                Object[] row = productStats.get(i);
                String name = String.valueOf(row[0]);
                double percentage = (double)row[2] / totalRevenue;

                int y = legendY + i * lineHeight;
                g2d.setColor(colors[i % colors.length]);
                g2d.fillRect(legendX, y + 1, swatch, swatch);
                g2d.setColor(Color.BLACK);

                String suffix = String.format(" (%.1f%%)", percentage * 100);
                int suffixWidth = g2d.getFontMetrics().stringWidth(suffix);
                String fitted = fitText(g2d, name,
                    Math.max(20, maxTextWidth - suffixWidth));
                g2d.drawString(fitted + suffix, textX, y + swatch);
            }

            if (hasOther) {
                int y = legendY + productStats.size() * lineHeight;
                g2d.setColor(Color.LIGHT_GRAY);
                g2d.fillRect(legendX, y + 1, swatch, swatch);
                g2d.setColor(Color.BLACK);

                double percentage = otherRevenue / totalRevenue;
                String suffix = String.format(" (%.1f%%)", percentage * 100);
                int suffixWidth = g2d.getFontMetrics().stringWidth(suffix);
                String fitted = fitText(g2d, I18n.tr("Other products"),
                    Math.max(20, maxTextWidth - suffixWidth));
                g2d.drawString(fitted + suffix, textX, y + swatch);
            }

            g2d.setFont(original);
        } finally {
            g2d.dispose();
        }
    }

    private void drawPiePercentage(Graphics2D g2d, int centerX, int centerY,
            int diameter, double startAngle, double arcAngle,
            double percentage, Color sliceColor) {
        if (percentage < 0.03 || arcAngle < 10) return;

        double middle = Math.toRadians(startAngle + arcAngle / 2.0);
        double radius = diameter * 0.32;
        int x = centerX + (int)Math.round(Math.cos(middle) * radius);
        int y = centerY - (int)Math.round(Math.sin(middle) * radius);

        String label = String.format("%.1f%%", percentage * 100);
        Font original = g2d.getFont();
        Font font = original.deriveFont(Font.BOLD,
            Math.max(10f, original.getSize2D() - 1f));
        g2d.setFont(font);

        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int textHeight = metrics.getAscent();

        // A small translucent background keeps percentages readable on any slice.
        Color old = g2d.getColor();
        g2d.setColor(new Color(255, 255, 255, 205));
        g2d.fillRoundRect(
            x - textWidth / 2 - 4,
            y - textHeight + 2,
            textWidth + 8,
            textHeight + 5,
            8, 8);
        g2d.setColor(Color.BLACK);
        g2d.drawString(label, x - textWidth / 2, y + 2);

        g2d.setColor(old);
        g2d.setFont(original);
    }

    private String fitText(Graphics2D g2d, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics metrics = g2d.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) return text;

        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        if (suffixWidth >= maxWidth) return suffix;

        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, mid).stripTrailing() + suffix;
            if (metrics.stringWidth(candidate) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, low).stripTrailing() + suffix;
    }

    private void drawCenteredMessage(Graphics2D g2d, int width, int height,
            String message) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 16f));
        int messageWidth = g2d.getFontMetrics().stringWidth(message);
        g2d.drawString(message, Math.max(5, (width - messageWidth) / 2),
            Math.max(20, height / 2));
    }

    public void refreshData() {
        loadData();
    }
}
