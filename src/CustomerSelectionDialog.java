import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class CustomerSelectionDialog extends JDialog {
    private JTextField searchField;
    private JTable customersTable;
    private DefaultTableModel tableModel;
    private Customer selectedCustomer;
    private boolean customerSelected = false;

    public CustomerSelectionDialog(JDialog parent) {
        super(parent, "Select Customer", true);

        setupWindow();
        initComponents();
        loadAllCustomers();
    }

    private void setupWindow() {
        setSize(700, 500);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Improved search panel
        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Customer"));

        JPanel searchInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(30);
        searchField.setToolTipText("Search by name, surname, email, phone or address");

        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        JButton newCustomerButton = new JButton("New Customer");

        // Real-time search as you type
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
        });

        // Enter key for search
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                }
            }
        });

        searchButton.addActionListener(e -> performSearch());
        clearButton.addActionListener(e -> {
            searchField.setText("");
            loadAllCustomers();
        });
        newCustomerButton.addActionListener(e -> createNewCustomer());

        searchInputPanel.add(new JLabel("Search:"));
        searchInputPanel.add(searchField);
        searchInputPanel.add(searchButton);
        searchInputPanel.add(clearButton);
        searchInputPanel.add(newCustomerButton);

        searchPanel.add(searchInputPanel, BorderLayout.CENTER);

        // Customers table with detailed columns
        String[] columns = {"ID", "Name", "Surname", "Email", "Phone", "City", "Full Address"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        customersTable = new JTable(tableModel);

        // Hide ID column but keep it for data
        customersTable.getColumnModel().getColumn(0).setMinWidth(0);
        customersTable.getColumnModel().getColumn(0).setMaxWidth(0);
        customersTable.getColumnModel().getColumn(0).setPreferredWidth(0);

        // Set column widths
        customersTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Name
        customersTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Surname
        customersTable.getColumnModel().getColumn(3).setPreferredWidth(180); // Email
        customersTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Phone
        customersTable.getColumnModel().getColumn(5).setPreferredWidth(100); // City
        customersTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Full Address

        // Double-click for selection
        customersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectCustomer();
                }
            }
        });

        // Selection with Enter
        customersTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectCustomer();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(customersTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Customers (Double-click to select)"));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton selectButton = new JButton("Select");
        JButton cancelButton = new JButton("Cancel");

        selectButton.addActionListener(e -> selectCustomer());
        cancelButton.addActionListener(e -> dispose());

        // Button styling
        selectButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.setPreferredSize(new Dimension(100, 30));
        selectButton.setFont(selectButton.getFont().deriveFont(Font.BOLD));

        buttonPanel.add(selectButton);
        buttonPanel.add(cancelButton);

        // Assembly
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Initial focus on search field
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }

    private void loadAllCustomers() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, first_name, last_name, email, phone, address
                FROM customers
                ORDER BY last_name, first_name
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    addCustomerRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading customers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void performSearch() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadAllCustomers();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, first_name, last_name, email, phone, address
                FROM customers
                WHERE LOWER(first_name) LIKE LOWER(?)
                   OR LOWER(last_name) LIKE LOWER(?)
                   OR LOWER(email) LIKE LOWER(?)
                   OR phone LIKE ?
                   OR LOWER(address) LIKE LOWER(?)
                ORDER BY
                    CASE
                        WHEN LOWER(last_name) LIKE LOWER(?) THEN 1
                        WHEN LOWER(first_name) LIKE LOWER(?) THEN 2
                        ELSE 3
                    END,
                    last_name, first_name
            """;

            String searchPattern = "%" + searchTerm + "%";
            String exactPattern = searchTerm + "%";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);
                pstmt.setString(5, searchPattern);
                pstmt.setString(6, exactPattern); // For sorting
                pstmt.setString(7, exactPattern); // For sorting

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        addCustomerRow(rs);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error searching customers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addCustomerRow(ResultSet rs) throws SQLException {
        Vector<Object> row = new Vector<>();
        row.add(rs.getInt("id")); // Hidden ID
        row.add(rs.getString("first_name"));
        row.add(rs.getString("last_name"));
        row.add(rs.getString("email"));
        row.add(rs.getString("phone"));

        // Extract the city from the address (take the last part after the comma)
        String fullAddress = rs.getString("address");
        String city = "";
        if (fullAddress != null && !fullAddress.isEmpty()) {
            String[] parts = fullAddress.split(",");
            if (parts.length > 1) {
                city = parts[parts.length - 1].trim();
            }
        }

        row.add(city);
        row.add(fullAddress);
        tableModel.addRow(row);
    }

    private void selectCustomer() {
        int selectedRow = customersTable.getSelectedRow();
        if (selectedRow != -1) {
            selectedCustomer = new Customer(
                (int)tableModel.getValueAt(selectedRow, 0),
                (String)tableModel.getValueAt(selectedRow, 1),
                (String)tableModel.getValueAt(selectedRow, 2),
                (String)tableModel.getValueAt(selectedRow, 3),
                (String)tableModel.getValueAt(selectedRow, 4),
                (String)tableModel.getValueAt(selectedRow, 6)
            );
            customerSelected = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a customer from the list",
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void createNewCustomer() {
        CustomerDialog dialog = new CustomerDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isCustomerSaved()) {
            // Reload the list and select the new customer
            loadAllCustomers();
            // If possible, find and select the newly created customer
            // (this would require returning the ID of the new customer from the dialog)
        }
    }

    public Customer getSelectedCustomer() {
        return selectedCustomer;
    }

    public boolean isCustomerSelected() {
        return customerSelected;
    }
}
