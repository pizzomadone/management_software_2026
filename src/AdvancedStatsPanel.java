import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AdvancedStatsPanel extends JPanel {
    private JTabbedPane tabbedPane;
    private SimpleDateFormat dateFormat;
    private JPanel salesChartPanel;
    private JPanel productsChartPanel;
    private JTable topProductsTable;
    private JComboBox<String> periodCombo;
    private Map<String, double[]> monthlySales;
    private List<Object[]> productStats;

    public AdvancedStatsPanel() {
        dateFormat = new SimpleDateFormat("MM/yyyy");
        monthlySales = new HashMap<>();
        productStats = new ArrayList<>();

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

        // Period panel
        JPanel periodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        periodCombo = new JComboBox<>(new String[]{"Last 6 months", "Last year", "Last 2 years"});
        periodCombo.addActionListener(e -> loadData());
        periodPanel.add(new JLabel("Period: "));
        periodPanel.add(periodCombo);

        // Sales tab
        JPanel salesPanel = new JPanel(new BorderLayout());
        salesChartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSalesChart(g);
            }
        };
        salesPanel.add(periodPanel, BorderLayout.NORTH);
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
        String[] columns = {"Product", "Quantity Sold", "Revenue", "% of Total"};
        topProductsTable = new JTable(new Object[0][4], columns);

        JPanel productsTopPanel = new JPanel(new GridLayout(2, 1));
        productsTopPanel.add(productsChartPanel);
        productsTopPanel.add(new JScrollPane(topProductsTable));
        productsPanel.add(productsTopPanel);

        tabbedPane.addTab("Sales Trend", salesPanel);
        tabbedPane.addTab("Product Analysis", productsPanel);

        add(tabbedPane);
    }

    private void loadData() {
        loadSalesData();
        loadProductsData();
        repaint();
    }

    private void loadSalesData() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            int months = switch(periodCombo.getSelectedIndex()) {
                case 0 -> 6;
                case 1 -> 12;
                case 2 -> 24;
                default -> 12;
            };

            // Query with proper period filtering
            String query = "SELECT strftime('%Y-%m', o.order_date) as month, " +
                          "SUM(o.total) as total, " +
                          "COUNT(*) as num_orders " +
                          "FROM orders o " +
                          "WHERE o.order_date IS NOT NULL " +
                          "AND o.order_date >= datetime('now', '-" + months + " months') " +
                          "GROUP BY month " +
                          "ORDER BY month";

            System.out.println("Loading sales data for last " + months + " months");

            // Thread-safe access to monthlySales
            Map<String, double[]> newMonthlySales = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    String month = rs.getString("month");
                    if (month != null && !month.trim().isEmpty()) {
                        double total = rs.getDouble("total");
                        int numOrders = rs.getInt("num_orders");
                        newMonthlySales.put(month, new double[]{total, numOrders});
                        System.out.println("Month: " + month + ", Total: €" + total + ", Orders: " + numOrders);
                    }
                }

                System.out.println("Loaded " + newMonthlySales.size() + " months of sales data");
            }

            // Update reference atomically
            monthlySales = newMonthlySales;

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading sales data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void drawSalesChart(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = salesChartPanel.getWidth();
        int height = salesChartPanel.getHeight();
        int padding = 50;

        // Safe access to monthlySales
        Map<String, double[]> currentSales = monthlySales; // Local reference
        if (currentSales.isEmpty()) {
            // Draw "No data" message
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            String message = "No sales data available";
            int messageWidth = g2d.getFontMetrics().stringWidth(message);
            g2d.drawString(message, (width - messageWidth) / 2, height / 2);
            return;
        }

        // Find max value
        double maxSale = currentSales.values().stream()
            .mapToDouble(values -> values[0])
            .max().orElse(0);

        if (maxSale == 0) {
            g2d.setColor(Color.BLACK);
            g2d.drawString("No sales in this period", padding, height / 2);
            return;
        }

        // Draw axes
        g2d.setColor(Color.BLACK);
        g2d.drawLine(padding, height - padding, width - padding, height - padding); // X
        g2d.drawLine(padding, padding, padding, height - padding); // Y

        // Draw points and lines
        List<String> months = new ArrayList<>(currentSales.keySet());
        Collections.sort(months);
        int numPoints = months.size();

        if (numPoints == 0) return;

        int xStep = Math.max(1, (width - 2 * padding) / Math.max(numPoints - 1, 1));
        int x = padding;
        Point prevPoint = null;

        for (String month : months) {
            double[] values = currentSales.get(month);
            double sale = values[0];
            int y = height - padding - (int)((sale / maxSale) * (height - 2 * padding));

            g2d.setColor(Color.BLUE);
            g2d.fillOval(x - 4, y - 4, 8, 8);

            if (prevPoint != null) {
                g2d.drawLine(prevPoint.x, prevPoint.y, x, y);
            }
            prevPoint = new Point(x, y);

            // Draw month label
            g2d.setColor(Color.BLACK);
            String label = month;
            if (label != null && label.length() >= 7) {
                label = label.substring(5, 7) + "/" + label.substring(0, 4); // MM/YYYY
            }
            g2d.rotate(Math.PI / 4, x, height - padding + 20);
            g2d.drawString(label, x - 15, height - padding + 20);
            g2d.rotate(-Math.PI / 4, x, height - padding + 20);

            x += xStep;
        }

        // Draw Y scale
        g2d.setColor(Color.BLACK);
        for (int i = 0; i <= 5; i++) {
            int y = height - padding - (i * (height - 2 * padding) / 5);
            double value = (maxSale * i) / 5;
            g2d.drawString(String.format("€ %.0f", value), 5, y + 5);
        }
    }

    private void loadProductsData() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            int months = switch(periodCombo.getSelectedIndex()) {
                case 0 -> 6;
                case 1 -> 12;
                case 2 -> 24;
                default -> 12;
            };

            String query = "SELECT COALESCE(p.name, 'Product N/A') as name, " +
                          "SUM(d.quantity) as total_quantity, " +
                          "SUM(d.quantity * d.unit_price) as revenue, " +
                          "COUNT(DISTINCT o.id) as num_orders " +
                          "FROM order_details d " +
                          "LEFT JOIN products p ON d.product_id = p.id " +
                          "LEFT JOIN orders o ON d.order_id = o.id " +
                          "WHERE o.order_date IS NOT NULL " +
                          "AND o.order_date >= datetime('now', '-" + months + " months') " +
                          "GROUP BY d.product_id, p.name " +
                          "ORDER BY revenue DESC " +
                          "LIMIT 10";

            System.out.println("Loading products data for last " + months + " months");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                double totalRevenue = 0;
                productStats.clear();

                while (rs.next()) {
                    String name = rs.getString("name");
                    int quantity = rs.getInt("total_quantity");
                    double revenue = rs.getDouble("revenue");
                    totalRevenue += revenue;
                    productStats.add(new Object[]{name, quantity, revenue});
                }

                System.out.println("Loaded " + productStats.size() + " products, total revenue: €" + totalRevenue);

                // Update table
                Object[][] data = new Object[productStats.size()][4];
                for (int i = 0; i < productStats.size(); i++) {
                    Object[] row = productStats.get(i);
                    data[i][0] = row[0];
                    data[i][1] = row[1];
                    data[i][2] = String.format("€ %.2f", (double)row[2]);
                    if (totalRevenue > 0) {
                        data[i][3] = String.format("%.1f%%", ((double)row[2] / totalRevenue) * 100);
                    } else {
                        data[i][3] = "0.0%";
                    }
                }

                topProductsTable.setModel(new javax.swing.table.DefaultTableModel(
                    data,
                    new String[]{"Product", "Quantity Sold", "Revenue", "% of Total"}
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading product data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void drawProductsChart(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = productsChartPanel.getWidth();
        int height = productsChartPanel.getHeight();
        int padding = 50;

        if (productStats.isEmpty()) {
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            String message = "No product data available";
            int messageWidth = g2d.getFontMetrics().stringWidth(message);
            g2d.drawString(message, (width - messageWidth) / 2, height / 2);
            return;
        }

        double totalRevenue = productStats.stream()
            .mapToDouble(row -> (double)row[2])
            .sum();

        if (totalRevenue == 0) {
            g2d.setColor(Color.BLACK);
            g2d.drawString("No revenue data", padding, height / 2);
            return;
        }

        int diameter = Math.min(width - 100, height - 100);
        int centerX = width / 2;
        int centerY = height / 2;

        double startAngle = 0;
        int legendY = padding;

        // Predefined colors for products
        Color[] colors = {
            new Color(255, 99, 132), new Color(54, 162, 235), new Color(255, 205, 86),
            new Color(75, 192, 192), new Color(153, 102, 255), new Color(255, 159, 64),
            new Color(201, 203, 207), new Color(255, 99, 255), new Color(99, 255, 132),
            new Color(132, 99, 255)
        };

        for (int i = 0; i < productStats.size() && i < 10; i++) {
            Object[] row = productStats.get(i);
            String name = (String)row[0];
            double revenue = (double)row[2];
            double percentage = (revenue / totalRevenue);
            double arcAngle = 360 * percentage;

            Color color = colors[i % colors.length];

            g2d.setColor(color);
            g2d.fillArc(centerX - diameter/2, centerY - diameter/2,
                        diameter, diameter,
                        (int)startAngle, (int)arcAngle);

            // Legend
            if (width > 400) { // Only if there's space
                g2d.fillRect(width - 200, legendY, 15, 15);
                g2d.setColor(Color.BLACK);
                String label = name != null && name.length() > 20 ? name.substring(0, 17) + "..." : name;
                g2d.drawString(String.format("%s (%.1f%%)",
                    label, percentage * 100),
                    width - 180, legendY + 12);
                legendY += 20;
            }

            startAngle += arcAngle;
        }
    }
}
