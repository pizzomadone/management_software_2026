import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class DashboardPanel extends JPanel {
    // Color palette - Sober blue-gray theme
    private static final Color PRIMARY_COLOR = new Color(44, 62, 80);      // Dark blue-gray
    private static final Color SECONDARY_COLOR = new Color(52, 73, 94);    // Medium blue-gray
    private static final Color ACCENT_COLOR = new Color(52, 152, 219);     // Blue
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);     // Green
    private static final Color WARNING_COLOR = new Color(230, 126, 34);    // Orange
    private static final Color DANGER_COLOR = new Color(231, 76, 60);      // Red
    private static final Color LIGHT_COLOR = new Color(236, 240, 241);     // Light gray
    private static final Color BORDER_COLOR = new Color(189, 195, 199);    // Gray

    private JLabel warehouseValueLabel;
    private JLabel lowStockCountLabel;
    private JLabel pendingOrdersLabel;
    private JLabel monthRevenueLabel;
    private JLabel avgMarginLabel;
    private JLabel zeroStockLabel;

    private JPanel alertsPanel;
    private JPanel pendingOrdersPanel;
    private JTable topProductsTable;
    private JTable abcAnalysisTable;

    private NumberFormat currencyFormat;
    private NumberFormat percentFormat;
    private SimpleDateFormat dateFormat;

    private MainWindow mainWindow;

    public DashboardPanel(MainWindow mainWindow) {
        this.mainWindow = mainWindow;

        currencyFormat = NumberFormat.getCurrencyInstance(Locale.ITALY);
        percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMinimumFractionDigits(1);
        percentFormat.setMaximumFractionDigits(1);
        dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupPanel();
        loadData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Main container with vertical scroll
        JPanel mainContainer = new JPanel();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.Y_AXIS));

        // Header
        mainContainer.add(createHeaderPanel());
        mainContainer.add(Box.createVerticalStrut(15));

        // KPI Cards
        mainContainer.add(createKPIPanel());
        mainContainer.add(Box.createVerticalStrut(15));

        // Quick Actions
        mainContainer.add(createQuickActionsPanel());
        mainContainer.add(Box.createVerticalStrut(15));

        // Alerts Section
        mainContainer.add(createAlertsSection());
        mainContainer.add(Box.createVerticalStrut(15));

        // Two columns: Pending Orders | Top Products
        JPanel middlePanel = new JPanel(new GridLayout(1, 2, 10, 0));
        middlePanel.add(createPendingOrdersSection());
        middlePanel.add(createTopProductsSection());
        mainContainer.add(middlePanel);
        mainContainer.add(Box.createVerticalStrut(15));

        // ABC Analysis
        mainContainer.add(createABCAnalysisSection());

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(mainContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel titleLabel = new JLabel("Dashboard - Management Overview");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(PRIMARY_COLOR);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFont(new Font("Arial", Font.PLAIN, 12));
        refreshButton.setFocusPainted(false);
        refreshButton.setBackground(ACCENT_COLOR);
        refreshButton.setForeground(Color.WHITE);
        refreshButton.setBorderPainted(false);
        refreshButton.addActionListener(e -> loadData());

        panel.add(titleLabel, BorderLayout.WEST);
        panel.add(refreshButton, BorderLayout.EAST);

        return panel;
    }

    private JPanel createKPIPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Key Performance Indicators",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        // Create 6 KPI cards with consistent color scheme
        panel.add(createKPICard("Warehouse Value", "0.00 €", SECONDARY_COLOR, "warehouseValue"));
        panel.add(createKPICard("Low Stock Products", "0", DANGER_COLOR, "lowStock"));
        panel.add(createKPICard("Pending Orders", "0", WARNING_COLOR, "pendingOrders"));
        panel.add(createKPICard("Monthly Revenue", "0.00 €", SUCCESS_COLOR, "monthRevenue"));
        panel.add(createKPICard("Average Margin", "0.0%", ACCENT_COLOR, "avgMargin"));
        panel.add(createKPICard("Zero Stock Products", "0", WARNING_COLOR, "zeroStock"));

        return panel;
    }

    private JPanel createKPICard(String title, String defaultValue, Color color, String type) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 11));
        titleLabel.setForeground(new Color(100, 100, 100));

        JLabel valueLabel = new JLabel(defaultValue);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 20));
        valueLabel.setForeground(color);

        // Store reference based on type
        switch (type) {
            case "warehouseValue" -> warehouseValueLabel = valueLabel;
            case "lowStock" -> lowStockCountLabel = valueLabel;
            case "pendingOrders" -> pendingOrdersLabel = valueLabel;
            case "monthRevenue" -> monthRevenueLabel = valueLabel;
            case "avgMargin" -> avgMarginLabel = valueLabel;
            case "zeroStock" -> zeroStockLabel = valueLabel;
        }

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createQuickActionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Quick Actions",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        panel.add(createQuickActionButton("New Order", ACCENT_COLOR, this::createNewOrder));
        panel.add(createQuickActionButton("New Invoice", SUCCESS_COLOR, this::createNewInvoice));
        panel.add(createQuickActionButton("Warehouse Movement", SECONDARY_COLOR, this::createWarehouseMovement));
        panel.add(createQuickActionButton("Manage Customers", ACCENT_COLOR, this::openCustomers));
        panel.add(createQuickActionButton("Sales Report", SECONDARY_COLOR, this::openSalesReport));

        return panel;
    }

    private JButton createQuickActionButton(String text, Color color, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 13));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);  // Critical: ensures background color is painted
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(170, 38));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 1),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        button.addActionListener(e -> action.run());

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private JPanel createAlertsSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(DANGER_COLOR, 2),
            "Alerts - Products Below Minimum Stock",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            DANGER_COLOR
        ));

        alertsPanel = new JPanel();
        alertsPanel.setLayout(new BoxLayout(alertsPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(alertsPanel);
        scrollPane.setPreferredSize(new Dimension(0, 120));
        scrollPane.setBorder(null);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPendingOrdersSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Pending Orders",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        pendingOrdersPanel = new JPanel();
        pendingOrdersPanel.setLayout(new BoxLayout(pendingOrdersPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(pendingOrdersPanel);
        scrollPane.setPreferredSize(new Dimension(0, 250));
        scrollPane.setBorder(null);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createTopProductsSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Top 10 Best-Selling Products",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        String[] columns = {"#", "Product", "Qty Sold", "Revenue"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        topProductsTable = new JTable(model);
        topProductsTable.setFont(new Font("Arial", Font.PLAIN, 12));
        topProductsTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        topProductsTable.setRowHeight(25);
        topProductsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Enable column sorting
        TableSorterUtil.enableSorting(topProductsTable);

        // Column widths
        topProductsTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        topProductsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        topProductsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        topProductsTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        JScrollPane scrollPane = new JScrollPane(topProductsTable);
        scrollPane.setPreferredSize(new Dimension(0, 250));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createABCAnalysisSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "ABC (Pareto) Analysis - Product Classification by Revenue",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        String[] columns = {"Class", "# Products", "% Products", "Total Revenue", "% Revenue", "Description"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        abcAnalysisTable = new JTable(model);
        abcAnalysisTable.setFont(new Font("Arial", Font.PLAIN, 12));
        abcAnalysisTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        abcAnalysisTable.setRowHeight(30);

        // Enable column sorting
        TableSorterUtil.enableSorting(abcAnalysisTable);

        JScrollPane scrollPane = new JScrollPane(abcAnalysisTable);
        scrollPane.setPreferredSize(new Dimension(0, 150));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // Data loading methods
    public void loadData() {
        SwingUtilities.invokeLater(() -> {
            loadKPIData();
            loadAlerts();
            loadPendingOrders();
            loadTopProducts();
            loadABCAnalysis();
        });
    }

    private void loadKPIData() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            // 1. Warehouse Value
            String warehouseQuery = "SELECT SUM(quantity * acquisition_cost) as total FROM products WHERE active = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(warehouseQuery)) {
                if (rs.next()) {
                    double value = rs.getDouble("total");
                    warehouseValueLabel.setText(currencyFormat.format(value));
                }
            }

            // 2. Low Stock Count
            String lowStockQuery = "SELECT COUNT(*) as count FROM products WHERE active = 1 AND quantity < minimum_quantity AND minimum_quantity > 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(lowStockQuery)) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    lowStockCountLabel.setText(String.valueOf(count));
                }
            }

            // 3. Pending Orders
            String pendingQuery = "SELECT COUNT(*) as count FROM orders WHERE status != 'Completed' AND status != 'Cancelled'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(pendingQuery)) {
                if (rs.next()) {
                    pendingOrdersLabel.setText(String.valueOf(rs.getInt("count")));
                }
            }

            // 4. Month Revenue
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            String monthStart = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime());

            String revenueQuery = "SELECT SUM(total) as revenue FROM invoices WHERE date >= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(revenueQuery)) {
                pstmt.setString(1, monthStart);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    double revenue = rs.getDouble("revenue");
                    monthRevenueLabel.setText(currencyFormat.format(revenue));
                }
            }

            // 5. Average Margin
            String marginQuery = """
                SELECT AVG(CASE
                    WHEN p.price > 0 AND p.acquisition_cost > 0
                    THEN ((p.price - p.acquisition_cost) / p.price) * 100
                    ELSE 0
                END) as avg_margin
                FROM products p
                WHERE p.active = 1 AND p.price > 0
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(marginQuery)) {
                if (rs.next()) {
                    double margin = rs.getDouble("avg_margin");
                    avgMarginLabel.setText(String.format("%.1f%%", margin));
                }
            }

            // 6. Zero Stock Count
            String zeroStockQuery = "SELECT COUNT(*) as count FROM products WHERE active = 1 AND quantity = 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(zeroStockQuery)) {
                if (rs.next()) {
                    zeroStockLabel.setText(String.valueOf(rs.getInt("count")));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading KPI data: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadAlerts() {
        alertsPanel.removeAll();

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT code, name, quantity, minimum_quantity, (minimum_quantity - quantity) as shortage
                FROM products
                WHERE active = 1 AND quantity < minimum_quantity AND minimum_quantity > 0
                ORDER BY shortage DESC
                LIMIT 10
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                boolean hasAlerts = false;
                while (rs.next()) {
                    hasAlerts = true;
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    int quantity = rs.getInt("quantity");
                    int minQuantity = rs.getInt("minimum_quantity");
                    int shortage = rs.getInt("shortage");

                    JPanel alertItem = createAlertItem(code, name, quantity, minQuantity, shortage);
                    alertsPanel.add(alertItem);
                    alertsPanel.add(Box.createVerticalStrut(5));
                }

                if (!hasAlerts) {
                    JLabel noAlerts = new JLabel("No products below minimum stock");
                    noAlerts.setFont(new Font("Arial", Font.BOLD, 13));
                    noAlerts.setForeground(SUCCESS_COLOR);
                    alertsPanel.add(noAlerts);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        alertsPanel.revalidate();
        alertsPanel.repaint();
    }

    private JPanel createAlertItem(String code, String name, int quantity, int minQuantity, int shortage) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DANGER_COLOR),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        panel.setBackground(new Color(255, 245, 245));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel productLabel = new JLabel(String.format("%s - %s", code, name));
        productLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel stockLabel = new JLabel(String.format("Stock: %d / Min: %d (Short: %d)",
            quantity, minQuantity, shortage));
        stockLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        stockLabel.setForeground(new Color(100, 40, 40));

        JButton actionButton = new JButton("View Product");
        actionButton.setFont(new Font("Arial", Font.PLAIN, 11));
        actionButton.setFocusPainted(false);
        actionButton.setBackground(DANGER_COLOR);
        actionButton.setForeground(Color.WHITE);
        actionButton.setBorderPainted(false);
        actionButton.addActionListener(e -> openProductAndHighlight(code));

        panel.add(productLabel, BorderLayout.WEST);
        panel.add(stockLabel, BorderLayout.CENTER);
        panel.add(actionButton, BorderLayout.EAST);

        return panel;
    }

    private void loadPendingOrders() {
        pendingOrdersPanel.removeAll();

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.id, o.order_date, o.status, o.total,
                       c.first_name || ' ' || c.last_name as customer_name,
                       julianday('now') - julianday(o.order_date) as days_old
                FROM orders o
                LEFT JOIN customers c ON o.customer_id = c.id
                WHERE o.status != 'Completed' AND o.status != 'Cancelled'
                ORDER BY o.order_date ASC
                LIMIT 15
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                boolean hasOrders = false;
                while (rs.next()) {
                    hasOrders = true;
                    int id = rs.getInt("id");
                    String date = rs.getString("order_date");
                    String status = rs.getString("status");
                    double total = rs.getDouble("total");
                    String customer = rs.getString("customer_name");
                    int daysOld = (int) rs.getDouble("days_old");

                    JPanel orderItem = createPendingOrderItem(id, date, status, total, customer, daysOld);
                    pendingOrdersPanel.add(orderItem);
                    pendingOrdersPanel.add(Box.createVerticalStrut(5));
                }

                if (!hasOrders) {
                    JLabel noOrders = new JLabel("No pending orders");
                    noOrders.setFont(new Font("Arial", Font.BOLD, 13));
                    noOrders.setForeground(SUCCESS_COLOR);
                    pendingOrdersPanel.add(noOrders);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        pendingOrdersPanel.revalidate();
        pendingOrdersPanel.repaint();
    }

    private JPanel createPendingOrderItem(int id, String date, String status, double total, String customer, int daysOld) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        Color borderColor = daysOld > 7 ? DANGER_COLOR : WARNING_COLOR;
        Color bgColor = daysOld > 7 ? new Color(255, 245, 245) : new Color(255, 250, 240);

        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        panel.setBackground(bgColor);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel idLabel = new JLabel(String.format("Order #%d - %s", id, status));
        idLabel.setFont(new Font("Arial", Font.BOLD, 12));

        String dateStr = date;
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date);
            dateStr = dateFormat.format(d);
        } catch (Exception ignored) {}

        JLabel detailsLabel = new JLabel(String.format("%s - %s - %d days old",
            customer != null ? customer : "N/A", dateStr, daysOld));
        detailsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        detailsLabel.setForeground(new Color(100, 100, 100));

        infoPanel.add(idLabel);
        infoPanel.add(detailsLabel);

        JLabel totalLabel = new JLabel(currencyFormat.format(total));
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        totalLabel.setForeground(SUCCESS_COLOR);

        JButton viewButton = new JButton("View");
        viewButton.setFont(new Font("Arial", Font.PLAIN, 11));
        viewButton.setFocusPainted(false);
        viewButton.setBackground(ACCENT_COLOR);
        viewButton.setForeground(Color.WHITE);
        viewButton.setContentAreaFilled(true);  // Fix: ensures background is painted
        viewButton.setOpaque(true);
        viewButton.setBorderPainted(false);
        viewButton.addActionListener(e -> openOrderAndHighlight(id));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(totalLabel);
        rightPanel.add(viewButton);

        panel.add(infoPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private void loadTopProducts() {
        DefaultTableModel model = (DefaultTableModel) topProductsTable.getModel();
        model.setRowCount(0);

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.name,
                       SUM(od.quantity) as total_qty,
                       SUM(od.quantity * od.unit_price) as total_revenue
                FROM order_details od
                JOIN products p ON od.product_id = p.id
                JOIN orders o ON od.order_id = o.id
                WHERE o.status = 'Completed'
                GROUP BY p.id, p.name
                ORDER BY total_qty DESC
                LIMIT 10
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                int rank = 1;
                while (rs.next()) {
                    String name = rs.getString("name");
                    int qty = rs.getInt("total_qty");
                    double revenue = rs.getDouble("total_revenue");

                    model.addRow(new Object[]{
                        rank++,
                        name,
                        qty,
                        currencyFormat.format(revenue)
                    });
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadABCAnalysis() {
        DefaultTableModel model = (DefaultTableModel) abcAnalysisTable.getModel();
        model.setRowCount(0);

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            // Get all products with their revenue, sorted by revenue
            String query = """
                SELECT p.id, p.name,
                       COALESCE(SUM(od.quantity * od.unit_price), 0) as revenue
                FROM products p
                LEFT JOIN order_details od ON p.id = od.product_id
                LEFT JOIN orders o ON od.order_id = o.id AND o.status = 'Completed'
                WHERE p.active = 1
                GROUP BY p.id, p.name
                ORDER BY revenue DESC
            """;

            List<ProductRevenue> products = new ArrayList<>();
            double totalRevenue = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    double revenue = rs.getDouble("revenue");
                    products.add(new ProductRevenue(rs.getString("name"), revenue));
                    totalRevenue += revenue;
                }
            }

            if (products.isEmpty() || totalRevenue == 0) {
                model.addRow(new Object[]{"N/A", "0", "0%", currencyFormat.format(0), "0%", "No data available"});
                return;
            }

            // Calculate ABC classes
            int totalProducts = products.size();
            double cumulativeRevenue = 0;
            int classACount = 0, classBCount = 0, classCCount = 0;
            double classARevenue = 0, classBRevenue = 0, classCRevenue = 0;

            for (ProductRevenue pr : products) {
                cumulativeRevenue += pr.revenue;
                double percentRevenue = (cumulativeRevenue / totalRevenue);

                if (percentRevenue <= 0.80) {
                    classACount++;
                    classARevenue += pr.revenue;
                } else if (percentRevenue <= 0.95) {
                    classBCount++;
                    classBRevenue += pr.revenue;
                } else {
                    classCCount++;
                    classCRevenue += pr.revenue;
                }
            }

            // Add rows to table
            model.addRow(new Object[]{
                "A",
                classACount,
                String.format("%.1f%%", (classACount * 100.0 / totalProducts)),
                currencyFormat.format(classARevenue),
                String.format("%.1f%%", (classARevenue * 100.0 / totalRevenue)),
                "High-value products - Priority focus"
            });

            model.addRow(new Object[]{
                "B",
                classBCount,
                String.format("%.1f%%", (classBCount * 100.0 / totalProducts)),
                currencyFormat.format(classBRevenue),
                String.format("%.1f%%", (classBRevenue * 100.0 / totalRevenue)),
                "Medium-value products - Monitor"
            });

            model.addRow(new Object[]{
                "C",
                classCCount,
                String.format("%.1f%%", (classCCount * 100.0 / totalProducts)),
                currencyFormat.format(classCRevenue),
                String.format("%.1f%%", (classCRevenue * 100.0 / totalRevenue)),
                "Low-value products - Consider liquidation"
            });

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Helper class for ABC analysis
    private static class ProductRevenue {
        String name;
        double revenue;

        ProductRevenue(String name, double revenue) {
            this.name = name;
            this.revenue = revenue;
        }
    }

    // Navigation methods
    private void createNewOrder() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("ORDERS");
            SwingUtilities.invokeLater(() -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JFrame) {
                    OrderDialog dialog = new OrderDialog((JFrame) window, null);
                    dialog.setVisible(true);
                    loadData(); // Refresh after dialog closes
                }
            });
        }
    }

    private void createNewInvoice() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("INVOICES");
            SwingUtilities.invokeLater(() -> {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JFrame) {
                    InvoiceDialog dialog = new InvoiceDialog((JFrame) window, null);
                    dialog.setVisible(true);
                    loadData();
                }
            });
        }
    }

    private void createWarehouseMovement() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("WAREHOUSE");
        }
    }

    private void openCustomers() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("CUSTOMERS");
        }
    }

    private void openSalesReport() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("SALES_REPORT");
        }
    }

    private void openProductsPanel() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("PRODUCTS");
        }
    }

    private void openOrdersPanel() {
        if (mainWindow != null) {
            mainWindow.showPanelByName("ORDERS");
        }
    }

    private void openOrderAndHighlight(int orderId) {
        if (mainWindow != null) {
            mainWindow.showOrdersAndSelect(orderId);
        }
    }

    private void openProductAndHighlight(String productCode) {
        if (mainWindow != null) {
            mainWindow.showProductsAndSelect(productCode);
        }
    }
}
