import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.LocalDate;
import java.time.YearMonth;

public class AdvancedStatsPanel extends JPanel {
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
    private Map<String, double[]> monthlySales;
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
        updateCustomRangeVisibility();

        // Sales tab
        JPanel salesPanel = new JPanel(new BorderLayout());
        salesChartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSalesChart(g);
            }
        };
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
        javax.swing.table.DefaultTableModel initialModel =
            new javax.swing.table.DefaultTableModel(new Object[0][4], columns) {
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

        add(periodPanel, BorderLayout.NORTH);
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

        // Pre-create every month in the selected interval. This makes the
        // difference between 6, 12 and 24 months visible even if a month has no sales.
        Map<String, double[]> newMonthlySales = new HashMap<>();
        YearMonth cursor = YearMonth.from(range.start());
        YearMonth last = YearMonth.from(range.end());
        while (!cursor.isAfter(last)) {
            newMonthlySales.put(cursor.toString(), new double[]{0.0, 0.0});
            cursor = cursor.plusMonths(1);
        }

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
    }

    private void drawSalesChart(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            int width = salesChartPanel.getWidth();
            int height = salesChartPanel.getHeight();
            int leftPadding = 70;
            int rightPadding = 30;
            int topPadding = 35;
            int bottomPadding = 72;

            Map<String, double[]> currentSales = monthlySales;
            if (currentSales.isEmpty()) {
                drawCenteredMessage(g2d, width, height, I18n.tr("No sales data available"));
                return;
            }

            double maxSale = currentSales.values().stream()
                .mapToDouble(values -> values[0]).max().orElse(0);

            List<String> months = new ArrayList<>(currentSales.keySet());
            Collections.sort(months);
            if (months.isEmpty()) return;

            int chartWidth = Math.max(1, width - leftPadding - rightPadding);
            int chartHeight = Math.max(1, height - topPadding - bottomPadding);

            g2d.setColor(Color.BLACK);
            g2d.drawLine(leftPadding, topPadding,
                leftPadding, topPadding + chartHeight);
            g2d.drawLine(leftPadding, topPadding + chartHeight,
                leftPadding + chartWidth, topPadding + chartHeight);

            if (maxSale <= 0) {
                g2d.drawString(I18n.tr("No sales in this period"),
                    leftPadding + 10, topPadding + chartHeight / 2);
            } else {
                Point previous = null;
                double step = months.size() <= 1
                    ? 0.0 : chartWidth / (double)(months.size() - 1);

                for (int i = 0; i < months.size(); i++) {
                    double sale = currentSales.get(months.get(i))[0];
                    int x = leftPadding + (int)Math.round(i * step);
                    int y = topPadding + chartHeight -
                        (int)Math.round((sale / maxSale) * chartHeight);

                    g2d.setColor(Color.BLUE);
                    g2d.fillOval(x - 4, y - 4, 8, 8);
                    if (previous != null)
                        g2d.drawLine(previous.x, previous.y, x, y);
                    previous = new Point(x, y);
                }
            }

            // Keep month labels legible for long periods instead of drawing
            // 24 overlapping labels.
            int maxLabels = Math.max(2, chartWidth / 85);
            int labelEvery = Math.max(1,
                (int)Math.ceil(months.size() / (double)maxLabels));
            g2d.setColor(Color.BLACK);
            double step = months.size() <= 1
                ? 0.0 : chartWidth / (double)(months.size() - 1);
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
                    int y = topPadding + chartHeight -
                        (i * chartHeight / 5);
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

        javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(
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

            // Reserve a dedicated column for the legend. This prevents the
            // colored product list from spilling below or over the pie chart.
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

                g2d.setColor(colors[i % colors.length]);
                g2d.fillArc(centerX - diameter / 2, centerY - diameter / 2,
                    diameter, diameter,
                    (int)Math.round(startAngle), (int)Math.ceil(arcAngle));
                startAngle += arcAngle;
            }

            if (hasOther) {
                g2d.setColor(Color.LIGHT_GRAY);
                double otherAngle = 360.0 * otherRevenue / totalRevenue;
                g2d.fillArc(centerX - diameter / 2, centerY - diameter / 2,
                    diameter, diameter,
                    (int)Math.round(startAngle), (int)Math.ceil(otherAngle));
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
                g2d.setFont(original.deriveFont(Math.max(10f, original.getSize2D() - 1f)));

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
