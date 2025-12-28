import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class SupplierDialog extends JDialog {
    private Supplier supplier;
    private boolean supplierSaved = false;

    private JTextField companyNameField;
    private JTextField vatNumberField;
    private JTextField taxCodeField;
    private JTextField addressField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField certifiedEmailField;
    private JTextField websiteField;
    private JTextArea notesArea;

    // Constructor for JFrame parent
    public SupplierDialog(JFrame parent, Supplier supplier) {
        super(parent, supplier == null ? "New Supplier" : "Edit Supplier", true);
        this.supplier = supplier;

        setupWindow();
        initComponents();
        if (supplier != null) {
            loadSupplierData();
        }
    }

    // Constructor for JDialog parent
    public SupplierDialog(JDialog parent, Supplier supplier) {
        super(parent, supplier == null ? "New Supplier" : "Edit Supplier", true);
        this.supplier = supplier;

        setupWindow();
        initComponents();
        if (supplier != null) {
            loadSupplierData();
        }
    }

    private void setupWindow() {
        setLayout(new BorderLayout(10, 10));
        pack();
        setMinimumSize(new Dimension(650, 550));
        setLocationRelativeTo(getOwner());
    }

    private void initComponents() {
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Company Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("* Company Name:"), gbc);

        gbc.gridx = 1;
        companyNameField = new JTextField(30);
        formPanel.add(companyNameField, gbc);

        // VAT Number
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("* VAT Number:"), gbc);

        gbc.gridx = 1;
        vatNumberField = new JTextField(20);
        formPanel.add(vatNumberField, gbc);

        // Tax Code
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Tax Code:"), gbc);

        gbc.gridx = 1;
        taxCodeField = new JTextField(20);
        formPanel.add(taxCodeField, gbc);

        // Address
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Address:"), gbc);

        gbc.gridx = 1;
        addressField = new JTextField(30);
        formPanel.add(addressField, gbc);

        // Phone
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Phone:"), gbc);

        gbc.gridx = 1;
        phoneField = new JTextField(20);
        formPanel.add(phoneField, gbc);

        // Email
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        emailField = new JTextField(30);
        formPanel.add(emailField, gbc);

        // Certified Email
        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("Certified Email:"), gbc);

        gbc.gridx = 1;
        certifiedEmailField = new JTextField(30);
        formPanel.add(certifiedEmailField, gbc);

        // Website
        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("Website:"), gbc);

        gbc.gridx = 1;
        websiteField = new JTextField(30);
        formPanel.add(websiteField, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        notesArea = new JTextArea(4, 30);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(notesArea), gbc);

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> saveSupplier());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Main layout
        add(new JScrollPane(formPanel), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Add legend for required fields
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        legendPanel.add(new JLabel("* Required fields"));
        add(legendPanel, BorderLayout.NORTH);
    }

    private void loadSupplierData() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM suppliers WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, supplier.getId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        companyNameField.setText(rs.getString("company_name"));
                        vatNumberField.setText(rs.getString("vat_number"));
                        taxCodeField.setText(rs.getString("tax_code"));
                        addressField.setText(rs.getString("address"));
                        phoneField.setText(rs.getString("phone"));
                        emailField.setText(rs.getString("email"));
                        certifiedEmailField.setText(rs.getString("certified_email"));
                        websiteField.setText(rs.getString("website"));
                        notesArea.setText(rs.getString("notes"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading supplier data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSupplier() {
        String companyName = companyNameField.getText().trim();
        String vatNumber = vatNumberField.getText().trim();

        if (companyName.isEmpty() || vatNumber.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please fill in all required fields.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            if (supplier == null) {
                // New supplier
                String insertQuery = """
                    INSERT INTO suppliers (company_name, vat_number, tax_code,
                                          address, phone, email, certified_email, website, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, companyName);
                    pstmt.setString(2, vatNumber);
                    pstmt.setString(3, taxCodeField.getText().trim());
                    pstmt.setString(4, addressField.getText().trim());
                    pstmt.setString(5, phoneField.getText().trim());
                    pstmt.setString(6, emailField.getText().trim());
                    pstmt.setString(7, certifiedEmailField.getText().trim());
                    pstmt.setString(8, websiteField.getText().trim());
                    pstmt.setString(9, notesArea.getText().trim());

                    pstmt.executeUpdate();

                    // Get the new supplier ID
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int newId = rs.getInt(1);
                            supplier = new Supplier(newId, companyName, vatNumber,
                                taxCodeField.getText().trim(), addressField.getText().trim(),
                                phoneField.getText().trim(), emailField.getText().trim(),
                                certifiedEmailField.getText().trim(), websiteField.getText().trim(),
                                notesArea.getText().trim());
                        }
                    }
                }
            } else {
                // Edit existing supplier
                String updateQuery = """
                    UPDATE suppliers
                    SET company_name = ?, vat_number = ?, tax_code = ?,
                        address = ?, phone = ?, email = ?, certified_email = ?,
                        website = ?, notes = ?
                    WHERE id = ?
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                    pstmt.setString(1, companyName);
                    pstmt.setString(2, vatNumber);
                    pstmt.setString(3, taxCodeField.getText().trim());
                    pstmt.setString(4, addressField.getText().trim());
                    pstmt.setString(5, phoneField.getText().trim());
                    pstmt.setString(6, emailField.getText().trim());
                    pstmt.setString(7, certifiedEmailField.getText().trim());
                    pstmt.setString(8, websiteField.getText().trim());
                    pstmt.setString(9, notesArea.getText().trim());
                    pstmt.setInt(10, supplier.getId());

                    pstmt.executeUpdate();
                }
            }

            supplierSaved = true;
            dispose();

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving the supplier: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSupplierSaved() {
        return supplierSaved;
    }
}
