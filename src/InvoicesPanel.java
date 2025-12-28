import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;

public class InvoicesPanel extends JPanel {
    private JTable invoicesTable;
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
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // Invoices table
        String[] columns = {"Number", "Date", "Customer", "Taxable Amount", "VAT", "Total", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        invoicesTable = new JTable(tableModel);
        invoicesTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

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
        generatePDFButton = new JButton("Generate Invoice");
        refreshButton = new JButton("Refresh");

        generatePDFButton.setFont(generatePDFButton.getFont().deriveFont(Font.BOLD));
        generatePDFButton.setPreferredSize(new Dimension(150, 30));
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
        add(new JScrollPane(invoicesTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = invoicesTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
        generatePDFButton.setEnabled(isRowSelected);
    }

    private void loadInvoices() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT i.number, i.date, i.customer_id, i.status,
                       c.first_name || ' ' || c.last_name as customer_name
                FROM invoices i
                LEFT JOIN customers c ON i.customer_id = c.id
                ORDER BY i.date DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String number = rs.getString("number");

                    // Recalculate totals from details
                    double[] totals = recalculateInvoiceTotals(number);
                    double taxableAmount = totals[0];
                    double vat = totals[1];
                    double total = totals[2];

                    Vector<Object> row = new Vector<>();
                    row.add(number);

                    Date date = DateUtils.parseDate(rs, "date");
                    if (date != null) {
                        row.add(DateUtils.formatDate(date, dateFormat));
                    } else {
                        row.add("");
                    }

                    row.add(rs.getString("customer_name"));
                    row.add(String.format("%.2f €", taxableAmount));
                    row.add(String.format("%.2f €", vat));
                    row.add(String.format("%.2f €", total));
                    row.add(rs.getString("status"));
                    tableModel.addRow(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error while loading invoices: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private double[] recalculateInvoiceTotals(String invoiceNumber) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT SUM(d.quantity * d.unit_price) as taxable_amount_calc,
                   SUM(d.quantity * d.unit_price * d.vat_rate / 100) as vat_calc
            FROM invoice_details d
            WHERE d.invoice_id = (SELECT id FROM invoices WHERE number = ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, invoiceNumber);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                double taxableAmount = rs.getDouble("taxable_amount_calc");
                double vat = rs.getDouble("vat_calc");
                double total = taxableAmount + vat;
                return new double[]{taxableAmount, vat, total};
            }
        }
        return new double[]{0.0, 0.0, 0.0};
    }

    private void searchInvoices() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadInvoices();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT i.number, i.date, i.customer_id, i.status,
                       c.first_name || ' ' || c.last_name as customer_name
                FROM invoices i
                LEFT JOIN customers c ON i.customer_id = c.id
                WHERE i.number LIKE ?
                   OR c.first_name LIKE ?
                   OR c.last_name LIKE ?
                   OR (c.first_name || ' ' || c.last_name) LIKE ?
                ORDER BY i.date DESC
            """;

            String searchPattern = "%" + searchTerm + "%";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String number = rs.getString("number");

                        // Recalculate totals
                        double[] totals = recalculateInvoiceTotals(number);

                        Vector<Object> row = new Vector<>();
                        row.add(number);

                        Date date = DateUtils.parseDate(rs, "date");
                        if (date != null) {
                            row.add(DateUtils.formatDate(date, dateFormat));
                        } else {
                            row.add("");
                        }

                        row.add(rs.getString("customer_name"));
                        row.add(String.format("%.2f €", totals[0]));
                        row.add(String.format("%.2f €", totals[1]));
                        row.add(String.format("%.2f €", totals[2]));
                        row.add(rs.getString("status"));
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error while searching for invoices: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(this,
                "Error while creating the invoice: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editSelectedInvoice() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            try {
                String number = (String)tableModel.getValueAt(selectedRow, 0);
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
                JOptionPane.showMessageDialog(this,
                    "Error while modifying the invoice: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateSelectedInvoicePDF() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            try {
                String number = (String)tableModel.getValueAt(selectedRow, 0);
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
                    JOptionPane.showMessageDialog(this,
                        "Unable to load invoice or customer data",
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                setCursor(Cursor.getDefaultCursor());
                JOptionPane.showMessageDialog(this,
                    "Error generating PDF: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Customer loadCustomerByInvoice(Invoice invoice) {
        if (invoice == null) return null;

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM customers WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, invoice.getCustomerId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Customer(
                            rs.getInt("id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("address")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Invoice loadInvoiceByNumber(String number) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT i.*, c.first_name || ' ' || c.last_name as customer_name
            FROM invoices i
            LEFT JOIN customers c ON i.customer_id = c.id
            WHERE i.number = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, number);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Date date = DateUtils.parseDate(rs, "date");
                    if (date == null) {
                        date = new Date();
                    }

                    // Recalculate totals
                    double[] totals = recalculateInvoiceTotals(number);

                    Invoice invoice = new Invoice(
                        rs.getInt("id"),
                        rs.getString("number"),
                        date,
                        rs.getInt("customer_id"),
                        rs.getString("customer_name"),
                        totals[0], // taxableAmount
                        totals[1], // vat
                        totals[2], // total
                        rs.getString("status")
                    );

                    loadInvoiceItems(invoice);
                    return invoice;
                }
            }
        }
        return null;
    }

    private void loadInvoiceItems(Invoice invoice) throws SQLException {
        String query = """
            SELECT i.*, p.name as product_name, p.code as product_code
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
                        rs.getDouble("unit_price"),
                        rs.getDouble("vat_rate"),
                        rs.getDouble("total")
                    );
                    invoice.getItems().add(item);
                }
            }
        }
    }

    private void deleteSelectedInvoice() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow != -1) {
            String number = (String)tableModel.getValueAt(selectedRow, 0);
            String customer = (String)tableModel.getValueAt(selectedRow, 2);
            String status = (String)tableModel.getValueAt(selectedRow, 6);

            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete invoice " + number + " from customer " + customer + "?\n" +
                "Status: " + status + "\n" +
                (status.equals("Issued") || status.equals("Paid") ? "Stock will be restored." : "No stock changes."),
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    conn.setAutoCommit(false);

                    try {
                        // Get invoice ID
                        int invoiceId = 0;
                        String getIdQuery = "SELECT id FROM invoices WHERE number = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(getIdQuery)) {
                            pstmt.setString(1, number);
                            ResultSet rs = pstmt.executeQuery();
                            if (rs.next()) {
                                invoiceId = rs.getInt("id");
                            }
                        }

                        // Restore stock based on invoice status
                        if (invoiceId > 0) {
                            StockManager.deleteInvoice(conn, invoiceId, number, status);
                        }

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

                        conn.commit();
                        loadInvoices();

                        JOptionPane.showMessageDialog(this,
                            "Invoice deleted successfully!" +
                            (status.equals("Issued") || status.equals("Paid") ? "\nStock has been restored." : ""),
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this,
                        "Error while deleting the invoice: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
