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
    private JLabel warehouseSaleValueLabel;
    private JLabel zeroCostCountLabel;
    private JLabel negativeStockCountLabel;
    private JLabel lowStockCountLabel;
    private JLabel pendingOrdersLabel;
    private JLabel monthRevenueLabel;
    private JLabel monthInvoicedLabel;
    private JLabel avgMarginLabel;
    private JLabel zeroStockLabel;

    private JPanel alertsPanel;
    private JPanel pendingOrdersPanel;
    private JTable topProductsTable;
    private JTable abcAnalysisTable;

    private NumberFormat percentFormat;
    private SimpleDateFormat dateFormat;

    private MainWindow mainWindow;

    public DashboardPanel(MainWindow mainWindow) {
        this.mainWindow = mainWindow;

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
        routeNestedMouseWheel(mainContainer, scrollPane);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void routeNestedMouseWheel(Container root, JScrollPane dashboardScrollPane) {
        for (Component component : root.getComponents()) {
            if (component instanceof JScrollPane nestedScrollPane) {
                nestedScrollPane.setWheelScrollingEnabled(false);
                nestedScrollPane.addMouseWheelListener(event -> {
                    JScrollBar bar = dashboardScrollPane.getVerticalScrollBar();
                    int increment = Math.max(1, bar.getUnitIncrement(event.getWheelRotation()));
                    bar.setValue(bar.getValue() + event.getUnitsToScroll() * increment);
                    event.consume();
                });
            } else if (component instanceof Container container) {
                routeNestedMouseWheel(container, dashboardScrollPane);
            }
        }
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
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Key Performance Indicators",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 14),
            PRIMARY_COLOR
        ));

        JPanel cards = new JPanel(new GridLayout(2, 4, 10, 10));
        cards.add(createKPICard("Stock at standard cost", "—", SECONDARY_COLOR, "warehouseValue"));
        cards.add(createKPICard("Stock at sale price", "—", SECONDARY_COLOR, "warehouseSaleValue"));
        cards.add(createKPICard("Low Stock Products", "—", DANGER_COLOR, "lowStock"));
        cards.add(createKPICard("Out of Stock Products", "—", WARNING_COLOR, "zeroStock"));
        cards.add(createKPICard("Monthly order sales (excl. VAT)", "—", SUCCESS_COLOR, "monthRevenue"));
        cards.add(createKPICard("Monthly invoiced (excl. VAT)", "—", SUCCESS_COLOR, "monthInvoiced"));
        cards.add(createKPICard("Pending Customer Orders", "—", WARNING_COLOR, "pendingOrders"));
        cards.add(createKPICard("Potential stock margin", "—", ACCENT_COLOR, "avgMargin"));
        panel.add(cards, BorderLayout.CENTER);

        JPanel notes = new JPanel(new GridLayout(0, 1, 0, 4));
        zeroCostCountLabel = new JLabel("—");
        negativeStockCountLabel = new JLabel("—");
        notes.add(createKPINote("In-stock products with zero purchase cost", zeroCostCountLabel));
        notes.add(createKPINote("Negative-stock products (excluded from valuation)", negativeStockCountLabel));
        panel.add(notes, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createKPINote(String title, JLabel value) {
        JPanel note = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        note.add(new JLabel(title));
        note.add(value);
        return note;
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
            case "warehouseSaleValue" -> warehouseSaleValueLabel = valueLabel;
            case "lowStock" -> lowStockCountLabel = valueLabel;
            case "pendingOrders" -> pendingOrdersLabel = valueLabel;
            case "monthRevenue" -> monthRevenueLabel = valueLabel;
            case "monthInvoiced" -> monthInvoicedLabel = valueLabel;
            case "avgMargin" -> avgMarginLabel = valueLabel;
            case "zeroStock" -> zeroStockLabel = valueLabel;
        }

        String explanation = "tooltip.kpi." + type;
        card.setToolTipText(explanation);
        titleLabel.setToolTipText(explanation);
        valueLabel.setToolTipText(explanation);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        JLabel help = new JLabel("?", SwingConstants.CENTER) {
            private Integer previousDismissDelay;

            {
                addHierarchyListener(event -> {
                    if (!isShowing()) restoreDismissDelay();
                });
            }

            @Override
            protected void processMouseEvent(java.awt.event.MouseEvent event) {
                if (event.getID() == java.awt.event.MouseEvent.MOUSE_ENTERED && previousDismissDelay == null) {
                    ToolTipManager manager = ToolTipManager.sharedInstance();
                    previousDismissDelay = manager.getDismissDelay();
                    manager.setDismissDelay(Integer.MAX_VALUE);
                }
                // Set the delay before Swing handles entry, including immediately reshown tooltips.
                super.processMouseEvent(event);
                if (event.getID() == java.awt.event.MouseEvent.MOUSE_EXITED) restoreDismissDelay();
            }

            private void restoreDismissDelay() {
                if (previousDismissDelay != null) {
                    ToolTipManager.sharedInstance().setDismissDelay(previousDismissDelay);
                    previousDismissDelay = null;
                }
            }
        };
        help.setFont(new Font("Arial", Font.BOLD, 11));
        help.setForeground(new Color(100, 100, 100));
        help.setPreferredSize(new Dimension(18, 18));
        help.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        help.setToolTipText(explanation);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(help);
        card.add(footer, BorderLayout.SOUTH);
        I18n.localize(card);

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

        panel.add(createQuickActionButton("New Customer Order", ACCENT_COLOR, this::createNewOrder));
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
        int translatedTextWidth = button.getFontMetrics(button.getFont())
            .stringWidth(I18n.tr(text));
        button.setPreferredSize(new Dimension(Math.max(170, translatedTextWidth + 48), 38));
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
            "Pending Customer Orders",
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

            // Cost and potential sale value are distinct. A stock deficit is not an asset.
            double warehouseValue = 0;
            double warehouseSaleValue = 0;
            int zeroCostCount = 0;
            int negativeStockCount = 0;
            String warehouseQuery = "SELECT quantity, acquisition_cost, price FROM products WHERE active = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(warehouseQuery)) {
                while (rs.next()) {
                    int quantity = Numbers.quantity(rs.getObject("quantity"));
                    double cost = Numbers.nonNegative(rs.getObject("acquisition_cost"));
                    double price = Numbers.nonNegative(rs.getObject("price"));
                    warehouseValue = Numbers.add(warehouseValue, Numbers.stockValue(quantity, cost));
                    warehouseSaleValue = Numbers.add(warehouseSaleValue, Numbers.stockValue(quantity, price));
                    if (quantity > 0 && cost == 0) zeroCostCount++;
                    if (quantity < 0) negativeStockCount++;
                }
            }

            // 2. Low Stock Count
            int lowStockCount = 0;
            String lowStockQuery = "SELECT COUNT(*) as count FROM products p " +
                "LEFT JOIN minimum_stock sm ON sm.product_id = p.id WHERE p.active = 1 AND " +
                "((p.quantity - p.reserved_quantity) < 0 OR " +
                "((p.quantity - p.reserved_quantity) < sm.minimum_quantity AND sm.minimum_quantity > 0))";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(lowStockQuery)) {
                if (rs.next()) {
                    lowStockCount = rs.getInt("count");
                }
            }

            // 3. Pending Orders
            int pendingOrders = 0;
            String pendingQuery = "SELECT COUNT(*) as count FROM orders WHERE status IN ('New','In Progress')";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(pendingQuery)) {
                if (rs.next()) {
                    pendingOrders = rs.getInt("count");
                }
            }

            // Orders and invoices measure different events; never add them together.
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long monthStart = cal.getTimeInMillis();
            cal.add(Calendar.MONTH, 1);
            long nextMonthStart = cal.getTimeInMillis();

            SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM-dd");
            String start = monthFormat.format(new java.util.Date(monthStart));
            String end = monthFormat.format(new java.util.Date(nextMonthStart));
            double monthlySales = sumMonthlyDocuments(conn,
                "SELECT total FROM orders WHERE status='Completed' AND " +
                    DateUtils.sqlDate("order_date") + ">=? AND " + DateUtils.sqlDate("order_date") + "<?", start, end);
            String monthlyInvoiced = monthlyInvoiceSummary(conn,start,end);

            // Potential gross margin uses the same stock totals, weighted by their sale value.
            // It is undefined without a positive denominator; it is not historical sales profit.
            String margin = warehouseSaleValue > 0
                ? String.format("%.1f%%", java.math.BigDecimal.valueOf(warehouseSaleValue)
                    .subtract(java.math.BigDecimal.valueOf(warehouseValue))
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .divide(java.math.BigDecimal.valueOf(warehouseSaleValue), 1, java.math.RoundingMode.HALF_UP))
                : "—";

            // Out of stock includes deficits, matching the warehouse report.
            int zeroStockCount = 0;
            String zeroStockQuery = "SELECT COUNT(*) as count FROM products WHERE active = 1 AND quantity <= 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(zeroStockQuery)) {
                if (rs.next()) {
                    zeroStockCount = rs.getInt("count");
                }
            }

            // Publish only after every query succeeds, so a failed refresh cannot mix old and new KPIs.
            warehouseValueLabel.setText(SettingsPanel.formatCurrency(warehouseValue));
            warehouseSaleValueLabel.setText(SettingsPanel.formatCurrency(warehouseSaleValue));
            zeroCostCountLabel.setText(String.valueOf(zeroCostCount));
            negativeStockCountLabel.setText(String.valueOf(negativeStockCount));
            negativeStockCountLabel.setForeground(negativeStockCount > 0 ? DANGER_COLOR : PRIMARY_COLOR);
            lowStockCountLabel.setText(String.valueOf(lowStockCount));
            pendingOrdersLabel.setText(String.valueOf(pendingOrders));
            monthRevenueLabel.setText(SettingsPanel.formatCurrency(monthlySales));
            monthInvoicedLabel.setText(monthlyInvoiced);
            avgMarginLabel.setText(margin);
            zeroStockLabel.setText(String.valueOf(zeroStockCount));

        } catch (SQLException | IllegalArgumentException e) {
            for (JLabel label : new JLabel[]{warehouseValueLabel, warehouseSaleValueLabel, zeroCostCountLabel,
                    negativeStockCountLabel, lowStockCountLabel, pendingOrdersLabel, monthRevenueLabel,
                    monthInvoicedLabel, avgMarginLabel, zeroStockLabel}) {
                label.setText("—");
            }
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading KPI data: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private String monthlyInvoiceSummary(Connection conn,String start,String end) throws SQLException {
        java.util.LinkedHashMap<String,Double> totals=new java.util.LinkedHashMap<>();
        String query="SELECT currency_code,taxable_amount FROM invoices WHERE status IN ('Issued','Paid') AND " +
            DateUtils.sqlDate("date") + ">=? AND " + DateUtils.sqlDate("date") + "<? ORDER BY currency_code,id";
        try(PreparedStatement stmt=conn.prepareStatement(query)) {
            stmt.setString(1,start);stmt.setString(2,end);
            try(ResultSet rs=stmt.executeQuery()) {
                while(rs.next()) {
                    String code=rs.getString("currency_code");
                    if(code==null || code.isBlank()) throw new SQLException("Invoice currency is missing");
                    double amount=Numbers.nonNegative(rs.getObject("taxable_amount"));
                    totals.put(code,Numbers.add(totals.getOrDefault(code,0.0),amount));
                }
            }
        }
        if(totals.isEmpty()) return SettingsPanel.formatCurrency(0);
        StringBuilder result=new StringBuilder();
        for(var entry:totals.entrySet()) {
            if(result.length()>0)result.append(" + ");
            result.append(SettingsPanel.formatCurrency(entry.getValue(),entry.getKey()));
        }
        return result.toString();
    }

    private double sumMonthlyDocuments(Connection conn, String query, String start, String end) throws SQLException {
        double total = 0;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, start);
            stmt.setString(2, end);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) total = Numbers.add(total, Numbers.nonNegative(rs.getObject("total")));
            }
        }
        return total;
    }

    private void loadAlerts() {
        alertsPanel.removeAll();

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.code, p.name, p.quantity, p.reserved_quantity,
                       (p.quantity - p.reserved_quantity) AS available_quantity,
                       COALESCE(sm.minimum_quantity, 0) AS minimum_quantity,
                       (COALESCE(sm.minimum_quantity, 0) - (p.quantity - p.reserved_quantity)) AS shortage
                FROM products p
                LEFT JOIN minimum_stock sm ON sm.product_id = p.id
                WHERE p.active = 1
                  AND ((p.quantity - p.reserved_quantity) < 0
                       OR ((p.quantity - p.reserved_quantity) < sm.minimum_quantity
                           AND sm.minimum_quantity > 0))
                ORDER BY ((p.quantity - p.reserved_quantity) < 0) DESC, shortage DESC, p.code
                LIMIT 10
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                boolean hasAlerts = false;
                while (rs.next()) {
                    hasAlerts = true;
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    int availableQuantity = rs.getInt("available_quantity");
                    int minQuantity = rs.getInt("minimum_quantity");
                    long shortage = rs.getLong("shortage");

                    JPanel alertItem = createAlertItem(code, name, availableQuantity, minQuantity, shortage);
                    alertsPanel.add(alertItem);
                    alertsPanel.add(Box.createVerticalStrut(5));
                }

                if (!hasAlerts) {
                    JLabel noAlerts = new JLabel(I18n.tr("No products below minimum stock"));
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

    private JPanel createAlertItem(String code, String name, int availableQuantity, int minQuantity, long shortage) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DANGER_COLOR),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        panel.setBackground(new Color(255, 245, 245));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel productLabel = new JLabel(String.format("%s - %s", code, name));
        productLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel stockLabel = new JLabel(String.format("%s: %d / %s: %d (%s: %d)",
            I18n.tr("Available"), availableQuantity, I18n.tr("Min"), minQuantity,
            I18n.tr("Short"), shortage));
        stockLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        stockLabel.setForeground(new Color(100, 40, 40));

        JButton actionButton = new JButton(I18n.tr("View"));
        actionButton.setFont(new Font("Arial", Font.PLAIN, 11));
        actionButton.setFocusPainted(false);
        actionButton.setBackground(ACCENT_COLOR);
        actionButton.setForeground(Color.WHITE);
        actionButton.setContentAreaFilled(true);  // Critical: ensures background is painted
        actionButton.setOpaque(true);
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
                    JLabel noOrders = new JLabel(I18n.tr("No pending orders"));
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

        JLabel idLabel = new JLabel(String.format("%s #%d - %s",
            I18n.tr("Customer Order"), id, I18n.tr(status)));
        idLabel.setFont(new Font("Arial", Font.BOLD, 12));

        String dateStr = date;
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date);
            dateStr = dateFormat.format(d);
        } catch (Exception ignored) {}

        JLabel detailsLabel = new JLabel(String.format("%s - %s - %d %s",
            customer != null ? customer : "N/A", dateStr, daysOld, I18n.tr("days old")));
        detailsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        detailsLabel.setForeground(new Color(100, 100, 100));

        infoPanel.add(idLabel);
        infoPanel.add(detailsLabel);

        JLabel totalLabel = new JLabel(SettingsPanel.formatCurrency(total));
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        totalLabel.setForeground(SUCCESS_COLOR);

        JButton viewButton = new JButton(I18n.tr("View"));
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
                       SUM(warestat_line_total(od.quantity,od.unit_price)) as total_revenue
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
                    long qty = rs.getLong("total_qty");
                    double revenue = Numbers.money(java.math.BigDecimal.valueOf(rs.getDouble("total_revenue")));

                    model.addRow(new Object[]{
                        rank++,
                        name,
                        qty,
                        SettingsPanel.formatCurrency(revenue)
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
                       COALESCE(SUM(CASE WHEN o.status='Completed' THEN warestat_line_total(od.quantity,od.unit_price) ELSE 0 END), 0) as revenue
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
                    double revenue = Numbers.money(java.math.BigDecimal.valueOf(rs.getDouble("revenue")));
                    products.add(new ProductRevenue(rs.getString("name"), revenue));
                    totalRevenue = Numbers.add(totalRevenue,revenue);
                }
            }

            if (products.isEmpty() || totalRevenue == 0) {
                model.addRow(new Object[]{"N/A", "0", "0%", SettingsPanel.formatCurrency(0), "0%", I18n.tr("No data available")});
                return;
            }

            // Calculate ABC classes
            int totalProducts = products.size();
            double cumulativeRevenue = 0;
            int classACount = 0, classBCount = 0, classCCount = 0;
            double classARevenue = 0, classBRevenue = 0, classCRevenue = 0;

            for (ProductRevenue pr : products) {
                double percentRevenue = cumulativeRevenue / totalRevenue;
                cumulativeRevenue = Numbers.add(cumulativeRevenue,pr.revenue);

                if (pr.revenue > 0 && percentRevenue < 0.80) {
                    classACount++;
                    classARevenue = Numbers.add(classARevenue,pr.revenue);
                } else if (pr.revenue > 0 && percentRevenue < 0.95) {
                    classBCount++;
                    classBRevenue = Numbers.add(classBRevenue,pr.revenue);
                } else {
                    classCCount++;
                    classCRevenue = Numbers.add(classCRevenue,pr.revenue);
                }
            }

            // Add rows to table
            model.addRow(new Object[]{
                "A",
                classACount,
                String.format("%.1f%%", (classACount * 100.0 / totalProducts)),
                SettingsPanel.formatCurrency(classARevenue),
                String.format("%.1f%%", (classARevenue * 100.0 / totalRevenue)),
                I18n.tr("High-value products - Priority focus")
            });

            model.addRow(new Object[]{
                "B",
                classBCount,
                String.format("%.1f%%", (classBCount * 100.0 / totalProducts)),
                SettingsPanel.formatCurrency(classBRevenue),
                String.format("%.1f%%", (classBRevenue * 100.0 / totalRevenue)),
                I18n.tr("Medium-value products - Monitor")
            });

            model.addRow(new Object[]{
                "C",
                classCCount,
                String.format("%.1f%%", (classCCount * 100.0 / totalProducts)),
                SettingsPanel.formatCurrency(classCRevenue),
                String.format("%.1f%%", (classCRevenue * 100.0 / totalRevenue)),
                I18n.tr("Low-value products - Consider liquidation")
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
