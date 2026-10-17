import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;

public class InvoicesPanel extends JPanel {
    private JTable invoicesTable;
    private JScrollPane invoicesScrollPane;
    private static final int INVOICE_BATCH_SIZE=100;
    private boolean moreInvoices=true;
    private boolean loadingInvoices;
    private String activeInvoiceSearch="";
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton generatePDFButton;
    private JButton refreshButton;
    private SimpleDateFormat dateFormat;

    public InvoicesPanel() {
        dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        setupPanel();
        initComponents();
        loadInvoices();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Search Panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Invoices"));

        searchField = new JTextField(25);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchInvoices());

        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(TableInteractionUtil.withClearButton(searchField, this::searchInvoices));
        searchPanel.add(searchButton);

        // Invoices table
        String[] columns = {"Number", "Date", "Customer", "Taxable Amount", "VAT", "Total", "Status", "Payment"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        invoicesTable = new JTable(tableModel);
        TableStyleUtil.applyCompact(invoicesTable);
        invoicesTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Enable column sorting with date support (column 1 is "Date")
        TableSorterUtil.enableSorting(invoicesTable, new int[]{1}, dateFormat);

        // Add Delete key shortcut
        TableInteractionUtil.addDeleteKeyAction(invoicesTable, this::deleteSelectedInvoice);

        // Add context menu
        TableInteractionUtil.addContextMenu(invoicesTable,
            new TableInteractionUtil.TableAction("New Invoice", this::createNewInvoice, false),
            new TableInteractionUtil.TableAction("Modify", this::editSelectedInvoice),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelectedInvoice),
            null, // Separator
            new TableInteractionUtil.TableAction("Generate PDF", this::generateSelectedInvoicePDF),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadInvoices, false)
        );

        // Double-click listener
        invoicesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int selectedRow = invoicesTable.getSelectedRow();
                    if (selectedRow != -1) {
                        editSelectedInvoice();
                    }
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("New Invoice");
        editButton = new JButton("Modify");
        deleteButton = new JButton("Delete");
        generatePDFButton = new JButton("Generate PDF");
        refreshButton = new JButton("Refresh");

        generatePDFButton.setFont(generatePDFButton.getFont().deriveFont(Font.BOLD));
        generatePDFButton.setPreferredSize(new Dimension(140, 30));
        generatePDFButton.setOpaque(true);

        addButton.addActionListener(e -> createNewInvoice());
        editButton.addActionListener(e -> editSelectedInvoice());
        deleteButton.addActionListener(e -> deleteSelectedInvoice());
        generatePDFButton.addActionListener(e -> generateSelectedInvoicePDF());
        refreshButton.addActionListener(e -> loadInvoices());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(generatePDFButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        invoicesScrollPane=new JScrollPane(invoicesTable);
        invoicesScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            JScrollBar bar=invoicesScrollPane.getVerticalScrollBar();
            if(moreInvoices && !loadingInvoices &&
               bar.getMaximum()>bar.getVisibleAmount() &&
               bar.getValue()+bar.getVisibleAmount()>=bar.getMaximum()-160)
                SwingUtilities.invokeLater(this::loadMoreInvoices);
        });
        add(invoicesScrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = invoicesTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
        generatePDFButton.setEnabled(isRowSelected);
    }

    public void loadInvoices() {
        activeInvoiceSearch="";
        if(searchField!=null)searchField.setText("");
        resetInvoiceResults();
    }

    private void searchInvoices() {
        activeInvoiceSearch=searchField.getText().trim();
        resetInvoiceResults();
    }

    private void resetInvoiceResults() {
        tableModel.setRowCount(0);
        moreInvoices=true;
        loadMoreInvoices();
        if(invoicesScrollPane!=null)
            invoicesScrollPane.getVerticalScrollBar().setValue(0);
    }

    private void loadMoreInvoices() {
        if(loadingInvoices || !moreInvoices)return;
        loadingInvoices=true;
        try {
            Connection conn=DatabaseManager.getInstance().getConnection();
            // Search is performed IN SQL, including historical snapshot names;
            // never iterate over tens of thousands of invoices in Java.
            String where=activeInvoiceSearch.isEmpty()?"":"""
                WHERE i.number LIKE ? OR CAST(i.id AS TEXT) LIKE ?
                    OR c.first_name LIKE ? OR c.last_name LIKE ?
                    OR i.customer_snapshot LIKE ?
                """;
            String query="""
                SELECT i.id, i.number, i.date, i.status, i.currency_code,
                       i.taxable_amount, i.vat, i.total, i.customer_snapshot,
                       i.order_id, i.payment_status AS invoice_payment_status,
                       i.paid_amount AS invoice_paid_amount,
                       o.payment_status AS order_payment_status,
                       o.paid_amount AS order_paid_amount,
                       c.first_name || ' ' || c.last_name AS customer_name
                FROM invoices i
                LEFT JOIN customers c ON i.customer_id=c.id
                LEFT JOIN orders o ON o.id=i.order_id
                """ + where + """
                ORDER BY i.date DESC,i.id DESC LIMIT ? OFFSET ?
                """;
            java.util.List<Vector<Object>> fetched=new java.util.ArrayList<>();
            boolean extraRow=false;
            try(PreparedStatement stmt=conn.prepareStatement(query)){
                int index=1;
                if(!activeInvoiceSearch.isEmpty()){
                    String pattern="%"+activeInvoiceSearch+"%";
                    for(int i=0;i<5;i++)stmt.setString(index++,pattern);
                }
                stmt.setInt(index++,INVOICE_BATCH_SIZE+1);
                stmt.setInt(index,tableModel.getRowCount());
                try(ResultSet rs=stmt.executeQuery()){
                    while(rs.next()){
                        if(fetched.size()>=INVOICE_BATCH_SIZE){
                            extraRow=true;
                            break;
                        }
                        String number=rs.getString("number");
                        Vector<Object> row=new Vector<>();
                        row.add(number);
                        Date date=DateUtils.parseDate(rs,"date");
                        row.add(date==null?"":DateUtils.formatDate(date,dateFormat));
                        row.add(snapshotCustomerName(rs.getString("customer_snapshot"),
                            rs.getString("customer_name")));
                        String currency=rs.getString("currency_code");
                        row.add(SettingsPanel.formatCurrency(rs.getDouble("taxable_amount"),currency));
                        row.add(SettingsPanel.formatCurrency(rs.getDouble("vat"),currency));
                        row.add(SettingsPanel.formatCurrency(rs.getDouble("total"),currency));
                        row.add(rs.getString("status"));
                        boolean linked=rs.getObject("order_id")!=null;
                        String paymentStatus=rs.getString(linked ?
                            "order_payment_status":"invoice_payment_status");
                        double paidAmount=rs.getDouble(linked ?
                            "order_paid_amount":"invoice_paid_amount");
                        row.add(formatPaymentStatus(paymentStatus,paidAmount,currency));
                        fetched.add(row);
                    }
                }
            }
            moreInvoices=extraRow;
            TableStyleUtil.appendRows(tableModel,fetched);
        }catch(Exception e){
            moreInvoices=false;
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading invoices: "+e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }finally {
            loadingInvoices=false;
        }
    }

    private double[] recalculateInvoiceTotals(String invoiceNumber) throws SQLException {
        try(PreparedStatement p=DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT taxable_amount,vat,total FROM invoices WHERE number=?")) {
            p.setString(1,invoiceNumber);try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Invoice no longer exists");
                return new double[]{r.getDouble(1),r.getDouble(2),r.getDouble(3)};
            }
        }
    }

    private void createNewInvoice() {
        try {
            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            InvoiceDialog dialog;
            if (parentWindow instanceof JFrame) {
                dialog = new InvoiceDialog((JFrame) parentWindow, null);
            } else {
                dialog = new InvoiceDialog((JDialog) parentWindow, null);
            }

            dialog.setVisible(true);
            if (dialog.isInvoiceSaved()) {
                loadInvoices();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error while creating the invoice: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editSelectedInvoice() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = invoicesTable.convertRowIndexToModel(selectedRow);
            try {
                String number = (String)tableModel.getValueAt(modelRow, 0);
                Invoice invoice = loadInvoiceByNumber(number);
                if (invoice != null) {
                    Window parentWindow = SwingUtilities.getWindowAncestor(this);

                    InvoiceDialog dialog;
                    if (parentWindow instanceof JFrame) {
                        dialog = new InvoiceDialog((JFrame) parentWindow, invoice);
                    } else {
                        dialog = new InvoiceDialog((JDialog) parentWindow, invoice);
                    }

                    dialog.setVisible(true);
                    if (dialog.isInvoiceSaved()) {
                        loadInvoices();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error while modifying the invoice: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateSelectedInvoicePDF() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = invoicesTable.convertRowIndexToModel(selectedRow);
            try {
                String number = (String)tableModel.getValueAt(modelRow, 0);
                Invoice invoice = loadInvoiceByNumber(number);
                Customer customer = loadCustomerByInvoice(invoice);

                if (invoice != null && customer != null) {
                    // Set wait cursor
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                    try {
                        // Generate PDF directly without SwingWorker to avoid blocking
                        InvoicePDFGenerator.generateInvoicePDF(invoice, customer, InvoicesPanel.this);
                    } finally {
                        // Restore default cursor
                        setCursor(Cursor.getDefaultCursor());
                    }

                } else {
                    Dialogs.showMessageDialog(this,
                        "Unable to load invoice or customer data",
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                setCursor(Cursor.getDefaultCursor());
                Dialogs.showMessageDialog(this,
                    "Error generating PDF: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Customer loadCustomerByInvoice(Invoice invoice) throws SQLException {
        return invoice==null?null:InvoiceSnapshots.customer(DatabaseManager.getInstance().getConnection(),invoice);
    }

    private Invoice loadInvoiceByNumber(String number) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT i.*,
                   o.payment_status AS order_payment_status,
                   o.paid_amount AS order_paid_amount,
                   c.first_name || ' ' || c.last_name as customer_name
            FROM invoices i
            LEFT JOIN customers c ON i.customer_id = c.id
            LEFT JOIN orders o ON o.id = i.order_id
            WHERE i.number = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, number);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Date date = DateUtils.parseDate(rs, "date");
                    if (date == null) {
                        throw new SQLException("Invoice contains an invalid date");
                    }

                    // Recalculate totals
                    double[] totals = recalculateInvoiceTotals(number);

                    Invoice invoice = new Invoice(
                        rs.getInt("id"),
                        rs.getString("number"),
                        date,
                        rs.getInt("customer_id"),
                        snapshotCustomerName(conn,rs.getInt("id"),rs.getString("customer_name")),
                        totals[0], // taxableAmount
                        totals[1], // vat
                        totals[2], // total
                        rs.getString("status")
                    );

                    Object orderValue = rs.getObject("order_id");
                    Integer orderId = orderValue == null ? null : ((Number)orderValue).intValue();
                    invoice.setOrderId(orderId);
                    if (orderId != null) {
                        String paymentStatus = rs.getString("order_payment_status");
                        invoice.setPaymentStatus(paymentStatus == null ? "NOT_PAID" : paymentStatus);
                        invoice.setPaidAmount(rs.getDouble("order_paid_amount"));
                    } else {
                        String paymentStatus = rs.getString("payment_status");
                        invoice.setPaymentStatus(paymentStatus == null ? "NOT_PAID" : paymentStatus);
                        invoice.setPaidAmount(rs.getDouble("paid_amount"));
                    }

                    loadInvoiceItems(invoice);
                    return invoice;
                }
            }
        }
        return null;
    }

    private void loadInvoiceItems(Invoice invoice) throws SQLException {
        String query = """
            SELECT i.*
            FROM invoice_details i
            LEFT JOIN products p ON i.product_id = p.id
            WHERE i.invoice_id = ?
        """;

        Connection conn = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, invoice.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    InvoiceItem item = new InvoiceItem(
                        rs.getInt("id"),
                        rs.getInt("invoice_id"),
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getString("product_code"),
                        rs.getInt("quantity"),
                        rs.getDouble("sale_unit_price_ex_vat"),
                        rs.getDouble("sale_unit_price_inc_vat"),
                        rs.getDouble("vat_rate"),
                        rs.getDouble("total")
                    );
                    invoice.getItems().add(item);
                }
            }
        }
    }

    private String formatPaymentStatus(String paymentStatus, double paidAmount, String currency) {
        if ("PAID".equals(paymentStatus)) return I18n.tr("Paid");
        if ("PARTIALLY_PAID".equals(paymentStatus))
            return I18n.tr("Partial") + " (" + SettingsPanel.formatCurrency(paidAmount, currency) + ")";
        return I18n.tr("Not Paid");
    }

    private void deleteSelectedInvoice() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = invoicesTable.convertRowIndexToModel(selectedRow);
            String number = (String)tableModel.getValueAt(modelRow, 0);
            String customer = (String)tableModel.getValueAt(modelRow, 2);
            String status = (String)tableModel.getValueAt(modelRow, 6);
        if (InvoiceDialog.isFinalizedStatus(status)) {
            Dialogs.showMessageDialog(this,I18n.tr("Issued and Canceled invoices are historical documents. Only status and payment can be changed."),I18n.tr("Historical Invoice"),JOptionPane.WARNING_MESSAGE);
            return;
        }


            int result = Dialogs.showConfirmDialog(this,
                "Are you sure you want to delete invoice " + number + " from customer " + customer + "?\n" +
                "Status: " + status,
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    DatabaseManager.beginTransaction(conn);

                    try {
                        // Get invoice ID and any linked completed order.
                        int invoiceId = 0;
                        Integer linkedOrderId = null;
                        String getIdQuery = "SELECT id,order_id FROM invoices WHERE number = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(getIdQuery)) {
                            pstmt.setString(1, number);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                if (rs.next()) {
                                    invoiceId = rs.getInt("id");
                                    Object orderValue = rs.getObject("order_id");
                                    if (orderValue != null)
                                        linkedOrderId = ((Number)orderValue).intValue();
                                }
                            }
                        }

                        // Invoices do not affect warehouse stock - stock is managed by orders only

                        // Delete the invoice details
                        String deleteDetailsQuery = "DELETE FROM invoice_details WHERE invoice_id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                            pstmt.setInt(1, invoiceId);
                            pstmt.executeUpdate();
                        }

                        // Delete the invoice
                        String deleteInvoiceQuery = "DELETE FROM invoices WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteInvoiceQuery)) {
                            pstmt.setInt(1, invoiceId);
                            pstmt.executeUpdate();
                        }

                        // Once a Draft invoice is removed, the order becomes the payment
                        // reference again. Normalize its payment against the order total.
                        if (linkedOrderId != null)
                            OrdersPanel.normalizeCompletedOrderPayment(conn, linkedOrderId);

                        conn.commit();
                        loadInvoices();

                        Dialogs.showMessageDialog(this,
                            "Invoice deleted successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    Dialogs.showMessageDialog(this,
                        "Error while deleting the invoice: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    public void selectInvoiceByNumber(String number) {
        if (number == null) return;
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            if (!number.equals(String.valueOf(tableModel.getValueAt(modelRow, 0)))) continue;
            int viewRow = invoicesTable.convertRowIndexToView(modelRow);
            if (viewRow >= 0) {
                invoicesTable.setRowSelectionInterval(viewRow, viewRow);
                invoicesTable.scrollRectToVisible(invoicesTable.getCellRect(viewRow, 0, true));
            }
            return;
        }
    }

    /**
     * Prefer the immutable invoice customer snapshot. On incomplete legacy
     * invoices, display the joined customer's name without extra SQL queries.
     */
    static String snapshotCustomerName(String snapshot,String currentName) throws SQLException {
        if(snapshot==null)return currentName==null?"":currentName;
        java.util.Properties v=InvoiceSnapshots.decode(snapshot);
        return (v.getProperty("first_name","")+" "+v.getProperty("last_name","")).trim();
    }

    private String snapshotCustomerName(Connection c,int invoiceId,String currentName)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT customer_snapshot FROM invoices WHERE id=?")) {
            p.setInt(1,invoiceId);try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Invoice no longer exists");
                return snapshotCustomerName(r.getString(1),currentName);
            }
        }
    }
}
