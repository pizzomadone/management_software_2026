import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** Creates one or more supplier orders by grouping selected products by supplier. */
public class MultiSupplierOrderDialog extends JDialog {
    private final DefaultTableModel model;
    private final JTable table;
    private final DatePickerField orderDate;
    private final DatePickerField deliveryDate;
    private final JComboBox<String> status;
    private final JTextArea notes;
    private final JLabel totalLabel;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private boolean saved;
    private boolean updating;

    public MultiSupplierOrderDialog(Window owner) {
        super(owner, "New Supplier Order", ModalityType.APPLICATION_MODAL);
        setSize(900, 620);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Order Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; header.add(new JLabel("Order Date:"), gbc);
        orderDate = new DatePickerField(DateUtils.formatDate(new java.util.Date(), dateFormat));
        gbc.gridx = 1; header.add(orderDate, gbc);
        gbc.gridx = 2; header.add(new JLabel("Delivery Date:"), gbc);
        deliveryDate = new DatePickerField(10, true);
        gbc.gridx = 3; header.add(deliveryDate, gbc);
        gbc.gridx = 0; gbc.gridy = 1; header.add(new JLabel("Status:"), gbc);
        status = new JComboBox<>(new String[]{"Draft", "Confirmed", "In Transit", "Completed", "Cancelled"});
        gbc.gridx = 1; header.add(status, gbc);
        gbc.gridx = 2; header.add(new JLabel("Notes:"), gbc);
        notes = new JTextArea(2, 28);
        notes.setLineWrap(true); notes.setWrapStyleWord(true);
        gbc.gridx = 3; header.add(new JScrollPane(notes), gbc);

        model = new DefaultTableModel(new String[]{"Product ID", "Code", "Product", "Supplier ID", "Supplier", "Quantity *", "Unit Price", "Total", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column == 5 || column == 6 || column == 8; }
        };
        table = new JTable(model);
        TableStyleUtil.applyCompact(table);
        table.setToolTipText(I18n.tr("Unit prices start from the supplier's current product price and can be edited for discounts or negotiated terms."));
        hideColumn(0); hideColumn(3);
        model.addTableModelListener(e -> {
            if (!updating && (e.getColumn() == 5 || e.getColumn() == 6)) SwingUtilities.invokeLater(this::previewTotals);
        });

        JPanel itemButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Product");
        JButton remove = new JButton("Remove Product");
        add.addActionListener(e -> selectProduct());
        remove.addActionListener(e -> removeProduct());
        itemButtons.add(add); itemButtons.add(remove);
        JButton reprice=new JButton("Refresh Supplier Prices");
        reprice.addActionListener(e -> refreshSupplierPrices());itemButtons.add(reprice);
        totalLabel = new JLabel("Total: " + SettingsPanel.formatCurrency(0));
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 14f));
        JPanel total = new JPanel(new FlowLayout(FlowLayout.RIGHT)); total.add(totalLabel);
        JPanel center = new JPanel(new BorderLayout());
        center.add(itemButtons, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(total, BorderLayout.SOUTH);

        JButton review = new JButton("Review and Confirm");
        JButton cancel = new JButton("Cancel");
        review.addActionListener(e -> reviewAndSave());
        cancel.addActionListener(e -> dispose());
        JPanel actions = new JPanel(); actions.add(review); actions.add(cancel);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH); root.add(center, BorderLayout.CENTER); root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        I18n.localize(this);
        Dialogs.centerWindow(this);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) Dialogs.centerWindow(this);
        super.setVisible(visible);
    }

    private void hideColumn(int modelIndex) {
        int view = table.convertColumnIndexToView(modelIndex);
        table.getColumnModel().getColumn(view).setMinWidth(0);
        table.getColumnModel().getColumn(view).setMaxWidth(0);
        table.getColumnModel().getColumn(view).setPreferredWidth(0);
    }

    private void selectProduct() {
        JDialog picker = new JDialog(this, "Select Product", true);
        DefaultTableModel products = new DefaultTableModel(new String[]{"ID", "Code", "Product", "Supplier ID", "Supplier", "Purchase Price", "Stock", "Preferred"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable productTable = new JTable(products);
        TableStyleUtil.applyCompact(productTable);
        JTextField search = new JTextField(25);
        Runnable load = () -> {
            loadProducts(products, search.getText().trim());
            if (products.getRowCount() > 0) productTable.setRowSelectionInterval(0, 0);
        };
        JButton searchButton = new JButton("Search"); searchButton.addActionListener(e -> load.run());
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:")); searchPanel.add(search); searchPanel.add(searchButton);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
        JButton choose = new JButton("Add");
        choose.addActionListener(e -> {
            int row = productTable.getSelectedRow();
            if (row < 0) { showWarning(picker, "Please select a product"); return; }
            row = productTable.convertRowIndexToModel(row);
            int id = (int) products.getValueAt(row, 0);
            try {
                Connection c = DatabaseManager.getInstance().getConnection();
                String missingCost = CostIntegrity.missingPurchaseCostMessage(
                    c, java.util.List.of(id));
                if (missingCost != null) {
                    Dialogs.showMessageDialog(picker, missingCost,
                        I18n.tr("Purchase Cost Required"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (SQLException ex) {
                showWarning(picker, I18n.tr(ex.getMessage()));
                return;
            }
            for (int i = 0; i < model.getRowCount(); i++) {
                if ((int) model.getValueAt(i, 0) == id) { showWarning(picker, "This product is already in the order"); return; }
            }
            double price; int qty;
            try {
                quantity.commitEdit(); qty = Numbers.positiveQuantity(quantity.getValue());
                Object supplier = products.getValueAt(row, 3);
                if (!(supplier instanceof Number)) throw new IllegalArgumentException(I18n.tr("Assign a supplier before ordering this product"));
                price = SupplierPricing.price(DatabaseManager.getInstance().getConnection(), ((Number)supplier).intValue(), id);
            } catch (Exception ex) { showWarning(picker,ex.getMessage()); return; }
            model.addRow(new Object[]{id, products.getValueAt(row, 1), products.getValueAt(row, 2),
                products.getValueAt(row, 3), products.getValueAt(row, 4), qty,
                price, Numbers.moneyText(Numbers.lineTotal(qty,price)), ""});
            updateTotals(); picker.dispose();
        });
        productTable.setAutoCreateRowSorter(true);
        productTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) choose.doClick(); }
        });
        JPanel bottom = new JPanel(); bottom.add(new JLabel("Quantity *:")); bottom.add(quantity); bottom.add(choose);
        JPanel pickerFooter = new JPanel(new BorderLayout());
        pickerFooter.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        pickerFooter.add(bottom, BorderLayout.EAST);
        picker.add(searchPanel, BorderLayout.NORTH); picker.add(new JScrollPane(productTable), BorderLayout.CENTER); picker.add(pickerFooter, BorderLayout.SOUTH);
        picker.setSize(760, 480); I18n.localize(picker); Dialogs.centerWindow(picker); load.run(); picker.setVisible(true);
    }

    private void loadProducts(DefaultTableModel products, String term) {
        products.setRowCount(0);
        String query = """
            SELECT p.id,p.code,p.name,ps.supplier_id,s.company_name,p.quantity,
                   ps.purchase_price,ps.is_preferred
            FROM product_suppliers ps
            JOIN products p ON p.id=ps.product_id
            JOIN suppliers s ON s.id=ps.supplier_id
            WHERE p.active=1 AND (?='' OR p.code LIKE ? OR p.name LIKE ? OR s.company_name LIKE ?)
            ORDER BY p.name,ps.is_preferred DESC,s.company_name
            """;
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(query)) {
            String pattern = "%" + term + "%";
            ps.setString(1, term); ps.setString(2, pattern); ps.setString(3, pattern); ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) products.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getObject(4), rs.getString(5), rs.getDouble(7), rs.getInt(6),
                    rs.getInt(8)==1?I18n.tr("Yes"):I18n.tr("No")});
            }
        } catch (SQLException ex) { showError(this, "Error loading products: " + ex.getMessage()); }
    }

    private void removeProduct() {
        int row = table.getSelectedRow();
        if (row >= 0) { model.removeRow(table.convertRowIndexToModel(row)); updateTotals(); }
    }

    private void refreshSupplierPrices() {
        try {
            Numbers.commitEdits(table);
            if(Dialogs.showConfirmDialog(this,I18n.tr("Replace all entered unit prices with the current prices saved for each supplier?"),I18n.tr("Refresh Supplier Prices"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            Connection c=DatabaseManager.getInstance().getConnection();double[] prices=new double[model.getRowCount()];
            for(int i=0;i<prices.length;i++)prices[i]=SupplierPricing.price(c,((Number)model.getValueAt(i,3)).intValue(),((Number)model.getValueAt(i,0)).intValue());
            for(int i=0;i<prices.length;i++)model.setValueAt(prices[i],i,6);
            updateTotals();
        } catch(Exception e) {showWarning(this,e.getMessage());}
    }

    private void previewTotals() {
        try { updateTotals(); } catch(IllegalArgumentException ex) {totalLabel.setText("Invalid quantity or price");}
    }

    private void updateTotals() {
        updating = true;
        try {
        double total = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            int quantity = parseInt(model.getValueAt(i, 5));
            double price = parseDouble(model.getValueAt(i, 6));
            double line = Numbers.lineTotal(quantity, price);
            model.setValueAt(Numbers.moneyText(line), i, 7); total = Numbers.add(total,line);
        }
        totalLabel.setText("Total: " + SettingsPanel.formatCurrency(total));
        } finally { updating = false; }
    }

    private void reviewAndSave() {
        try {
            Numbers.commitEdits(table);
            for (int row=0;row<model.getRowCount();row++) {
                Numbers.positiveQuantity(model.getValueAt(row,5)); parsePurchasePrice(model.getValueAt(row,6));
            }
            updateTotals();
        } catch (IllegalArgumentException e) { showWarning(this,e.getMessage()); return; }
        if (model.getRowCount() == 0) { showWarning(this, "Add at least one product to the order"); return; }

        try {
            java.util.List<Integer> productIds = new java.util.ArrayList<>();
            for (int row=0; row<model.getRowCount(); row++)
                productIds.add(((Number)model.getValueAt(row,0)).intValue());
            String missingCost = CostIntegrity.missingPurchaseCostMessage(
                DatabaseManager.getInstance().getConnection(), productIds);
            if (missingCost != null) {
                Dialogs.showMessageDialog(this, missingCost,
                    I18n.tr("Purchase Cost Required"), JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (SQLException ex) {
            showWarning(this, I18n.tr(ex.getMessage()));
            return;
        }

        java.util.Date date;
        java.util.Date expected = null;
        try {
            date = DateUtils.parseDate(orderDate.getText(), dateFormat);
            if (!deliveryDate.getText().trim().isEmpty()) expected = DateUtils.parseDate(deliveryDate.getText(), dateFormat);
            if (date == null) throw new IllegalArgumentException();
        } catch (Exception ex) { showWarning(this, "Invalid date format. Use dd/MM/yyyy format"); return; }

        if (expected != null && expected.before(date)) {
            showWarning(this, "Delivery date cannot be before order date");
            return;
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            Object supplier = model.getValueAt(i, 3);
            if (!(supplier instanceof Number) || parseInt(model.getValueAt(i, 5)) <= 0) {
                showWarning(this, I18n.format(
                    "Every product must have a supplier, a positive quantity and a purchase price greater than zero. Check: {0}",
                    model.getValueAt(i, 2)));
                return;
            }
            groups.computeIfAbsent(((Number) supplier).intValue(), key -> new ArrayList<>()).add(i);
        }
        StringBuilder summary = new StringBuilder(
            I18n.format("The selection will create {0} separate supplier order(s):", groups.size()) + "\n\n");
        double grandTotal = 0;
        for (List<Integer> rows : groups.values()) {
            int first = rows.get(0); double subtotal = 0;
            summary.append(model.getValueAt(first, 4)).append(" - ")
                .append(I18n.format("{0} product(s)", rows.size())).append("\n");
            for (int row : rows) subtotal = Numbers.add(subtotal,parseDouble(model.getValueAt(row, 7)));
            summary.append(I18n.tr("Subtotal")).append(": ")
                .append(SettingsPanel.formatCurrency(subtotal)).append("\n\n");
            grandTotal = Numbers.add(grandTotal,subtotal);
        }
        summary.append(I18n.tr("Grand total")).append(": ")
            .append(SettingsPanel.formatCurrency(grandTotal));
        JTextArea area = new JTextArea(summary.toString(), 12, 48); area.setEditable(false); area.setOpaque(false);
        int answer = Dialogs.showConfirmDialog(this, new JScrollPane(area), "Confirm Supplier Order Split", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        saveGroups(groups, date, expected);
    }

    private void saveGroups(Map<Integer, List<Integer>> groups, java.util.Date date, java.util.Date expected) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); DatabaseManager.beginTransaction(conn);
            java.util.Date receiptDate = "Completed".equals(status.getSelectedItem())
                ? new java.util.Date() : null;
            String prefix = "OF" + new java.text.SimpleDateFormat("yyyy").format(date);
            int sequence = nextSequence(conn, prefix);
            for (Map.Entry<Integer, List<Integer>> group : groups.entrySet()) {
                String number = DocumentNumbers.nextSupplierNumber(conn,date);
                double total = 0;
                for (int row : group.getValue()) total=Numbers.add(total,Numbers.lineTotal(parseInt(model.getValueAt(row,5)),parsePurchasePrice(model.getValueAt(row,6))));
                int orderId;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO supplier_orders (supplier_id,number,order_date,expected_delivery_date,received_date,status,total,notes) VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, group.getKey()); ps.setString(2, number); ps.setTimestamp(3, DateUtils.toSqlTimestamp(date));
                    ps.setTimestamp(4, expected == null ? null : DateUtils.toSqlTimestamp(expected));
                    ps.setTimestamp(5, receiptDate == null ? null : DateUtils.toSqlTimestamp(receiptDate));
                    ps.setString(6, (String) status.getSelectedItem());
                    ps.setDouble(7, total); ps.setString(8, notes.getText().trim()); ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Unable to create supplier order"); orderId = keys.getInt(1); }
                }
                List<StockManager.StockItem> stock = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO supplier_order_details (order_id,product_id,quantity,unit_price,total,notes) VALUES (?,?,?,?,?,?)")) {
                    for (int row : group.getValue()) {
                        int productId = (int) model.getValueAt(row, 0); int qty = parseInt(model.getValueAt(row, 5));
                        ps.setInt(1, orderId); ps.setInt(2, productId); ps.setInt(3, qty); ps.setDouble(4, parsePurchasePrice(model.getValueAt(row, 6)));
                        ps.setDouble(5, Numbers.lineTotal(qty,parsePurchasePrice(model.getValueAt(row,6)))); ps.setString(6, String.valueOf(model.getValueAt(row, 8))); ps.addBatch();
                        stock.add(new StockManager.StockItem(productId, String.valueOf(model.getValueAt(row, 2)), qty));
                    }
                    ps.executeBatch();
                }
                if ("Completed".equals(status.getSelectedItem()))
                    InventoryLotManager.receiveSupplierOrder(conn,orderId,receiptDate,number);
            }
            conn.commit(); saved = true; dispose();
        } catch (Exception ex) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            showError(this, "No orders were created. Error saving supplier orders: " + ex.getMessage());
        } finally { if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {} }
    }

    private int nextSequence(Connection conn, String prefix) throws SQLException {
        return DocumentNumbers.nextSupplierSequence(conn,prefix);
    }

    private int parseInt(Object value) { return Numbers.positiveQuantity(value); }
    private double parseDouble(Object value) { return Numbers.nonNegative(value); }
    private double parsePurchasePrice(Object value) {
        double price = Numbers.nonNegative(value);
        if (price <= 0) throw new IllegalArgumentException(
            I18n.tr("Purchase price must be greater than zero"));
        return price;
    }
    private void showWarning(Component parent, String message) { Dialogs.showMessageDialog(parent, message, "Warning", JOptionPane.WARNING_MESSAGE); }
    private void showError(Component parent, String message) { Dialogs.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE); }
    public boolean isSaved() { return saved; }
}
