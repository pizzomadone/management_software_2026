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
    // Calm dashboard palette: strong colors are reserved for meaning, not framing.
    private static final Color PRIMARY_COLOR = new Color(48, 65, 82);
    private static final Color SECONDARY_COLOR = new Color(82, 96, 110);
    private static final Color ACCENT_COLOR = new Color(52, 126, 180);
    private static final Color SUCCESS_COLOR = new Color(42, 155, 96);
    private static final Color WARNING_COLOR = new Color(210, 132, 50);
    private static final Color DANGER_COLOR = new Color(205, 78, 68);
    private static final Color LIGHT_COLOR = new Color(245, 247, 249);
    private static final Color SURFACE_COLOR = new Color(252, 252, 253);
    private static final Color BORDER_COLOR = new Color(218, 223, 228);
    private static final Color MUTED_TEXT_COLOR = new Color(102, 111, 120);
    private static final Color SOFT_DANGER_COLOR = new Color(255, 249, 248);
    private static final Color SOFT_WARNING_COLOR = new Color(255, 251, 245);
    private static final Color SOFT_ACCENT_COLOR = new Color(244, 249, 253);

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
    private JLabel costCompletenessNoteLabel;
    private JLabel historicalSalesCostNoteLabel;

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
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        setBackground(LIGHT_COLOR);

        JPanel mainContainer = new ViewportWidthPanel();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.Y_AXIS));
        mainContainer.setBackground(LIGHT_COLOR);

        mainContainer.add(createHeaderPanel());
        mainContainer.add(Box.createVerticalStrut(12));
        mainContainer.add(createKPIPanel());
        mainContainer.add(Box.createVerticalStrut(12));
        mainContainer.add(createQuickActionsPanel());
        mainContainer.add(Box.createVerticalStrut(12));
        mainContainer.add(createAlertsSection());
        mainContainer.add(Box.createVerticalStrut(12));

        JPanel middlePanel = new JPanel(new GridLayout(1, 2, 12, 0));
        middlePanel.setOpaque(false);
        middlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        middlePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        middlePanel.add(createPendingOrdersSection());
        middlePanel.add(createTopProductsSection());
        mainContainer.add(middlePanel);
        mainContainer.add(Box.createVerticalStrut(12));

        mainContainer.add(createABCAnalysisSection());

        JScrollPane scrollPane = new JScrollPane(mainContainer);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(LIGHT_COLOR);
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
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createEmptyBorder(3, 2, 1, 2));

        JLabel titleLabel = new JLabel("Dashboard - Management Overview");
        titleLabel.setFont(dashboardFont(Font.BOLD, 21f));
        titleLabel.setForeground(PRIMARY_COLOR);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFont(dashboardFont(Font.PLAIN, 12f));
        refreshButton.setFocusPainted(false);
        refreshButton.setBackground(SOFT_ACCENT_COLOR);
        refreshButton.setForeground(ACCENT_COLOR.darker());
        refreshButton.setContentAreaFilled(true);
        refreshButton.setOpaque(true);
        refreshButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        refreshButton.addActionListener(e -> loadData());

        panel.add(titleLabel, BorderLayout.WEST);
        panel.add(refreshButton, BorderLayout.EAST);
        return panel;
    }

    private JPanel createKPIPanel() {
        JPanel panel = createSectionPanel("Key Performance Indicators", PRIMARY_COLOR);

        JPanel cards = new JPanel(new GridLayout(2, 4, 10, 10));
        cards.setOpaque(false);
        cards.add(createKPICard("Stock at preferred supplier cost", "—", SECONDARY_COLOR, "warehouseValue"));
        cards.add(createKPICard("Stock at net sale price", "—", SECONDARY_COLOR, "warehouseSaleValue"));
        cards.add(createKPICard("Low Stock Products", "—", DANGER_COLOR, "lowStock"));
        cards.add(createKPICard("Out of Stock Products", "—", WARNING_COLOR, "zeroStock"));
        cards.add(createKPICard("Monthly order sales (excl. VAT)", "—", SUCCESS_COLOR, "monthRevenue"));
        cards.add(createKPICard("Monthly invoiced (excl. VAT)", "—", SUCCESS_COLOR, "monthInvoiced"));
        cards.add(createKPICard("Pending Customer Orders", "—", WARNING_COLOR, "pendingOrders"));
        cards.add(createKPICard("Potential stock margin", "—", ACCENT_COLOR, "avgMargin"));
        panel.add(cards, BorderLayout.CENTER);

        JPanel notes = new JPanel(new GridLayout(0, 1, 0, 3));
        notes.setOpaque(false);
        notes.setBorder(BorderFactory.createEmptyBorder(9, 2, 0, 2));
        zeroCostCountLabel = new JLabel("—");
        negativeStockCountLabel = new JLabel("—");
        notes.add(createKPINote("In-stock products with missing preferred supplier cost", zeroCostCountLabel));
        notes.add(createKPINote("Negative-stock products (excluded from valuation)", negativeStockCountLabel));
        costCompletenessNoteLabel = new JLabel();
        costCompletenessNoteLabel.setFont(dashboardFont(Font.PLAIN, 11f));
        costCompletenessNoteLabel.setForeground(MUTED_TEXT_COLOR);
        historicalSalesCostNoteLabel = new JLabel();
        historicalSalesCostNoteLabel.setFont(dashboardFont(Font.PLAIN, 11f));
        historicalSalesCostNoteLabel.setForeground(MUTED_TEXT_COLOR);
        notes.add(costCompletenessNoteLabel);
        notes.add(historicalSalesCostNoteLabel);
        panel.add(notes, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createKPINote(String title, JLabel value) {
        JPanel note = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        note.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(dashboardFont(Font.PLAIN, 11.5f));
        titleLabel.setForeground(MUTED_TEXT_COLOR);
        value.setFont(dashboardFont(Font.BOLD, 11.5f));
        value.setForeground(PRIMARY_COLOR);
        note.add(titleLabel);
        note.add(value);
        return note;
    }

    private JPanel createKPICard(String title, String defaultValue, Color color, String type) {
        JPanel card = new DashboardSurfacePanel(
            new BorderLayout(5, 5), SURFACE_COLOR, BORDER_COLOR, color, 12);
        card.setBorder(BorderFactory.createEmptyBorder(10, 13, 8, 11));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(dashboardFont(Font.BOLD, 11f));
        titleLabel.setForeground(MUTED_TEXT_COLOR);

        JLabel valueLabel = new JLabel(defaultValue);
        valueLabel.setFont(dashboardFont(Font.BOLD, 20f));
        valueLabel.setForeground(color);

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
            private Popup helpPopup;

            {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addHierarchyListener(event -> {
                    if (!isShowing()) hideHelpPopup();
                });
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event))
                            showHelpPopup();
                    }

                    @Override public void mouseExited(java.awt.event.MouseEvent event) {
                        hideHelpPopup();
                    }
                });
            }

            // Keep the tooltip text available for localization/accessibility,
            // but suppress Swing's ordinary hover-triggered tooltip.
            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                return null;
            }

            private void showHelpPopup() {
                hideHelpPopup();
                String text = getToolTipText();
                if (text == null || text.isBlank() || !isShowing()) return;

                JToolTip tip = createToolTip();
                tip.setTipText(text);
                Dimension size = tip.getPreferredSize();
                Point location;
                try {
                    location = getLocationOnScreen();
                } catch (IllegalComponentStateException exception) {
                    return;
                }

                int x = location.x + getWidth() - size.width;
                int y = location.y + getHeight() + 4;
                helpPopup = PopupFactory.getSharedInstance()
                    .getPopup(this, tip, x, y);
                helpPopup.show();
            }

            private void hideHelpPopup() {
                if (helpPopup != null) {
                    helpPopup.hide();
                    helpPopup = null;
                }
            }
        };
        help.setFont(dashboardFont(Font.BOLD, 10f));
        help.setForeground(MUTED_TEXT_COLOR);
        help.setPreferredSize(new Dimension(16, 16));
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
        JPanel panel = createSectionPanel("Quick Actions", PRIMARY_COLOR);

        JPanel actions = new JPanel(new GridLayout(1, 0, 8, 0));
        actions.setOpaque(false);
        actions.add(createQuickActionButton("New Customer Order", ACCENT_COLOR, this::createNewOrder));
        actions.add(createQuickActionButton("New Invoice", SUCCESS_COLOR, this::createNewInvoice));
        actions.add(createQuickActionButton("Warehouse Movement", SECONDARY_COLOR, this::createWarehouseMovement));
        actions.add(createQuickActionButton("Manage Customers", ACCENT_COLOR, this::openCustomers));
        actions.add(createQuickActionButton("Sales Report", SECONDARY_COLOR, this::openSalesReport));

        panel.add(actions, BorderLayout.CENTER);
        return panel;
    }

    private JButton createQuickActionButton(String text, Color color, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(dashboardFont(Font.BOLD, 12f));
        button.setForeground(color.darker());
        button.setBackground(tint(color, 0.90));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        int translatedTextWidth = button.getFontMetrics(button.getFont())
            .stringWidth(I18n.tr(text));
        button.setPreferredSize(new Dimension(
            Math.max(155, translatedTextWidth + 34), 34));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(tint(color, 0.58)),
            BorderFactory.createEmptyBorder(4, 11, 4, 11)
        ));
        button.addActionListener(e -> action.run());

        Color normal = button.getBackground();
        Color hover = tint(color, 0.84);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(hover);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(normal);
            }
        });
        return button;
    }

    private JPanel createAlertsSection() {
        JPanel panel = createSectionPanel(
            "Alerts - Products Below Minimum Stock", DANGER_COLOR);

        alertsPanel = new JPanel();
        alertsPanel.setLayout(new BoxLayout(alertsPanel, BoxLayout.Y_AXIS));
        alertsPanel.setOpaque(false);
        alertsPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        JScrollPane scrollPane = new JScrollPane(alertsPanel);
        configureDashboardScrollPane(scrollPane);
        scrollPane.setPreferredSize(new Dimension(0, 132));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPendingOrdersSection() {
        JPanel panel = createSectionPanel("Pending Customer Orders", PRIMARY_COLOR);

        pendingOrdersPanel = new JPanel();
        pendingOrdersPanel.setLayout(new BoxLayout(pendingOrdersPanel, BoxLayout.Y_AXIS));
        pendingOrdersPanel.setOpaque(false);
        pendingOrdersPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        JScrollPane scrollPane = new JScrollPane(pendingOrdersPanel);
        configureDashboardScrollPane(scrollPane);
        scrollPane.setPreferredSize(new Dimension(0, 250));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTopProductsSection() {
        JPanel panel = createSectionPanel("Top 10 Best-Selling Products", PRIMARY_COLOR);

        String[] columns = {"#", "Product", "Qty Sold", "Revenue"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        topProductsTable = new JTable(model);
        TableStyleUtil.applyCompact(topProductsTable);
        topProductsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableSorterUtil.enableSorting(topProductsTable);

        topProductsTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        topProductsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        topProductsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        topProductsTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        JScrollPane scrollPane = new JScrollPane(topProductsTable);
        configureDashboardScrollPane(scrollPane);
        scrollPane.setPreferredSize(new Dimension(0, 250));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createABCAnalysisSection() {
        JPanel panel = createSectionPanel(
            "ABC (Pareto) Analysis - Product Classification by Revenue",
            PRIMARY_COLOR);

        String[] columns = {"Class", "# Products", "% Products", "Total Revenue", "% Revenue", "Description"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        abcAnalysisTable = new JTable(model);
        TableStyleUtil.applyCompact(abcAnalysisTable);
        TableSorterUtil.enableSorting(abcAnalysisTable);

        JScrollPane scrollPane = new JScrollPane(abcAnalysisTable);
        configureDashboardScrollPane(scrollPane);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSectionPanel(String title, Color titleColor) {
        JPanel panel = new DashboardSurfacePanel(
            new BorderLayout(0, 8), SURFACE_COLOR, BORDER_COLOR, null, 12);
        panel.setBorder(BorderFactory.createEmptyBorder(9, 10, 10, 10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(dashboardFont(Font.BOLD, 14f));
        titleLabel.setForeground(titleColor);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 1, 1, 1));
        header.add(titleLabel, BorderLayout.WEST);
        panel.add(header, BorderLayout.NORTH);

        I18n.localize(panel);
        return panel;
    }

    private void configureDashboardScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);
        scrollPane.getViewport().setBackground(SURFACE_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
    }

    private JButton createCompactActionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(dashboardFont(Font.PLAIN, 11f));
        button.setForeground(ACCENT_COLOR.darker());
        button.setBackground(SOFT_ACCENT_COLOR);
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(tint(ACCENT_COLOR, 0.62)),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        int width = Math.max(82,
            button.getFontMetrics(button.getFont()).stringWidth(text) + 24);
        button.setPreferredSize(new Dimension(width, 28));
        button.addActionListener(e -> action.run());
        return button;
    }

    private Font dashboardFont(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return base.deriveFont(style, size);
    }

    private static Color tint(Color color, double whiteAmount) {
        double amount = Math.max(0.0, Math.min(1.0, whiteAmount));
        int r = (int)Math.round(color.getRed() * (1.0 - amount) + 255 * amount);
        int g = (int)Math.round(color.getGreen() * (1.0 - amount) + 255 * amount);
        int b = (int)Math.round(color.getBlue() * (1.0 - amount) + 255 * amount);
        return new Color(r, g, b);
    }

    // Data loading methods

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
            String warehouseQuery = """
                SELECT p.quantity, p.sale_price_ex_vat,
                       COALESCE(ps.purchase_price,0) AS preferred_supplier_cost
                FROM products p
                LEFT JOIN product_suppliers ps
                  ON ps.product_id=p.id AND ps.is_preferred=1
                WHERE p.active=1
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(warehouseQuery)) {
                while (rs.next()) {
                    int quantity = Numbers.quantity(rs.getObject("quantity"));
                    double cost = Numbers.nonNegative(rs.getObject("preferred_supplier_cost"));
                    double price = Numbers.nonNegative(rs.getObject("sale_price_ex_vat"));
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
                "(p.quantity - p.reserved_quantity) > 0 AND " +
                "(p.quantity - p.reserved_quantity) < sm.minimum_quantity AND sm.minimum_quantity > 0";
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
                "SELECT total_ex_vat AS total FROM orders WHERE status='Completed' AND " +
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

            // Operational stock uses available stock: physical quantity minus reservations.
            int zeroStockCount = 0;
            String zeroStockQuery = "SELECT COUNT(*) as count FROM products WHERE active = 1 AND (quantity - reserved_quantity) <= 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(zeroStockQuery)) {
                if (rs.next()) {
                    zeroStockCount = rs.getInt("count");
                }
            }

            CostIntegrity.Audit costAudit = CostIntegrity.audit(conn);

            // Publish only after every query succeeds, so a failed refresh cannot mix old and new KPIs.
            String incompleteMarker = zeroCostCount > 0 ? " *" : "";
            warehouseValueLabel.setText(SettingsPanel.formatCurrency(warehouseValue) + incompleteMarker);
            warehouseSaleValueLabel.setText(SettingsPanel.formatCurrency(warehouseSaleValue));
            zeroCostCountLabel.setText(String.valueOf(zeroCostCount));
            negativeStockCountLabel.setText(String.valueOf(negativeStockCount));
            negativeStockCountLabel.setForeground(negativeStockCount > 0 ? DANGER_COLOR : PRIMARY_COLOR);
            lowStockCountLabel.setText(String.valueOf(lowStockCount));
            pendingOrdersLabel.setText(String.valueOf(pendingOrders));
            monthRevenueLabel.setText(SettingsPanel.formatCurrency(monthlySales));
            monthInvoicedLabel.setText(monthlyInvoiced);
            avgMarginLabel.setText(margin + (zeroCostCount > 0 && !"—".equals(margin) ? " *" : ""));
            zeroStockLabel.setText(String.valueOf(zeroStockCount));
            costCompletenessNoteLabel.setText(zeroCostCount > 0
                ? I18n.tr("* Some in-stock products have no preferred supplier cost; warehouse cost value and potential margin are incomplete.")
                : "");
            historicalSalesCostNoteLabel.setText(costAudit.salesWithoutHistoricalCost() > 0
                ? I18n.tr("* Some imported sales have no reconstructable historical cost and are excluded from historical cost and gross profit calculations.")
                : "");

        } catch (SQLException | IllegalArgumentException e) {
            for (JLabel label : new JLabel[]{warehouseValueLabel, warehouseSaleValueLabel, zeroCostCountLabel,
                    negativeStockCountLabel, lowStockCountLabel, pendingOrdersLabel, monthRevenueLabel,
                    monthInvoicedLabel, avgMarginLabel, zeroStockLabel}) {
                label.setText("—");
            }
            if (costCompletenessNoteLabel != null) costCompletenessNoteLabel.setText("");
            if (historicalSalesCostNoteLabel != null) historicalSalesCostNoteLabel.setText("");
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading KPI data: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private String monthlyInvoiceSummary(Connection conn,String start,String end) throws SQLException {
        java.util.LinkedHashMap<String,Double> totals=new java.util.LinkedHashMap<>();
        String query="SELECT currency_code,taxable_amount FROM invoices WHERE status='Issued' AND " +
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

    private JPanel createAlertItem(String code, String name, int availableQuantity,
            int minQuantity, long shortage) {
        JPanel panel = new DashboardSurfacePanel(
            new BorderLayout(12, 0), SOFT_DANGER_COLOR, BORDER_COLOR, DANGER_COLOR, 10);
        panel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 9));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel productLabel = new JLabel(String.format("%s - %s", code, name));
        productLabel.setFont(dashboardFont(Font.BOLD, 12f));
        productLabel.setForeground(PRIMARY_COLOR);

        JLabel stockLabel = new JLabel(String.format("%s: %d / %s: %d (%s: %d)",
            I18n.tr("Available"), availableQuantity, I18n.tr("Min"), minQuantity,
            I18n.tr("Short"), shortage));
        stockLabel.setFont(dashboardFont(Font.PLAIN, 11f));
        stockLabel.setForeground(DANGER_COLOR.darker());

        textPanel.add(productLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(stockLabel);

        JButton actionButton = createCompactActionButton(
            I18n.tr("View"), () -> openProductAndHighlight(code));

        JPanel actionWrap = new JPanel(new GridBagLayout());
        actionWrap.setOpaque(false);
        actionWrap.add(actionButton);

        panel.add(textPanel, BorderLayout.CENTER);
        panel.add(actionWrap, BorderLayout.EAST);
        return panel;
    }

    private void loadPendingOrders() {
        pendingOrdersPanel.removeAll();

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.id, o.order_date, o.status,
                       o.total_inc_vat AS total_inc_vat,
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
                    double total = rs.getDouble("total_inc_vat");
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

    private JPanel createPendingOrderItem(int id, String date, String status,
            double total, String customer, int daysOld) {
        Color accent = daysOld > 7 ? DANGER_COLOR : WARNING_COLOR;
        Color surface = daysOld > 7 ? SOFT_DANGER_COLOR : SOFT_WARNING_COLOR;

        JPanel panel = new DashboardSurfacePanel(
            new BorderLayout(10, 0), surface, BORDER_COLOR, accent, 10);
        panel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 9));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel idLabel = new JLabel(String.format("%s #%d - %s",
            I18n.tr("Customer Order"), id, I18n.tr(status)));
        idLabel.setFont(dashboardFont(Font.BOLD, 12f));
        idLabel.setForeground(PRIMARY_COLOR);

        String dateStr = date;
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date);
            dateStr = dateFormat.format(d);
        } catch (Exception ignored) {}

        JLabel detailsLabel = new JLabel(String.format("%s - %s - %d %s",
            customer != null ? customer : "N/A", dateStr, daysOld,
            I18n.tr("days old")));
        detailsLabel.setFont(dashboardFont(Font.PLAIN, 11f));
        detailsLabel.setForeground(MUTED_TEXT_COLOR);

        infoPanel.add(idLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(detailsLabel);

        JLabel totalLabel = new JLabel(SettingsPanel.formatCurrency(total));
        totalLabel.setFont(dashboardFont(Font.BOLD, 13f));
        totalLabel.setForeground(SUCCESS_COLOR.darker());

        JButton viewButton = createCompactActionButton(
            I18n.tr("View"), () -> openOrderAndHighlight(id));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        rightPanel.setOpaque(false);
        rightPanel.add(totalLabel);
        rightPanel.add(viewButton);

        panel.add(infoPanel, BorderLayout.CENTER);
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
                       SUM(warestat_line_total(od.quantity,od.sale_unit_price_ex_vat)) as total_revenue
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
                       COALESCE(SUM(CASE WHEN o.status='Completed' THEN warestat_line_total(od.quantity,od.sale_unit_price_ex_vat) ELSE 0 END), 0) as revenue
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

    private static final class ViewportWidthPanel extends JPanel
            implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect,
                int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                int orientation, int direction) {
            return Math.max(48, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class DashboardSurfacePanel extends JPanel {
        private final Color fill;
        private final Color outline;
        private final Color accent;
        private final int radius;

        DashboardSurfacePanel(LayoutManager layout, Color fill,
                Color outline, Color accent, int radius) {
            super(layout);
            this.fill = fill;
            this.outline = outline;
            this.accent = accent;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1),
                    Math.max(0, getHeight() - 1), radius, radius);
                if (accent != null && getHeight() > 12) {
                    g.setColor(accent);
                    g.fillRoundRect(1, 6, 4, Math.max(4, getHeight() - 12), 4, 4);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(outline);
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1),
                    Math.max(0, getHeight() - 1), radius, radius);
            } finally {
                g.dispose();
            }
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


