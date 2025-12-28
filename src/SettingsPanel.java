import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Properties;
import java.io.*;

public class SettingsPanel extends JPanel {
    private static final String SETTINGS_FILE = "app_settings.properties";
    private static Properties settings = new Properties();

    private JSlider fontSizeSlider;
    private JComboBox<String> themeCombo;
    private JCheckBox autoBackupCheck;
    private JSpinner backupIntervalSpinner;
    private JLabel previewLabel;

    // PDF Settings
    private JTextField pdfDirectoryField;
    private JButton browsePdfButton;

    // Currency and VAT Settings
    private JComboBox<String> currencyCombo;
    private JTextField defaultVatField;

    // Company Data fields
    private JTextField companyNameField;
    private JTextField vatNumberField;
    private JTextField taxCodeField;
    private JTextField addressField;
    private JTextField cityField;
    private JTextField postalCodeField;
    private JTextField countryField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField websiteField;
    private JTextField logoPathField;

    public SettingsPanel() {
        loadSettings();
        setupPanel();
        initComponents();
        applyCurrentSettings();
        loadCompanyData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Appearance Tab
        JPanel appearancePanel = createAppearancePanel();
        tabbedPane.addTab("Appearance", appearancePanel);

        // General Tab
        JPanel generalPanel = createGeneralPanel();
        tabbedPane.addTab("General", generalPanel);

        // Company Data Tab
        JPanel companyPanel = createCompanyDataPanel();
        tabbedPane.addTab("Company Data", companyPanel);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        JButton saveButton = new JButton("Save");

        applyButton.addActionListener(e -> applyAllSettings());
        saveButton.addActionListener(e -> saveAllSettings());

        buttonPanel.add(applyButton);
        buttonPanel.add(saveButton);

        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createAppearancePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Font Size
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Font Size:"), gbc);

        gbc.gridx = 1;
        fontSizeSlider = new JSlider(8, 24, getCurrentFontSize());
        fontSizeSlider.setMajorTickSpacing(4);
        fontSizeSlider.setMinorTickSpacing(2);
        fontSizeSlider.setPaintTicks(true);
        fontSizeSlider.setPaintLabels(true);
        fontSizeSlider.addChangeListener(e -> updatePreview());
        panel.add(fontSizeSlider, gbc);

        // Theme
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Theme:"), gbc);

        gbc.gridx = 1;
        themeCombo = new JComboBox<>(new String[]{"System", "Metal", "Nimbus"});
        themeCombo.setSelectedItem(getSetting("theme", "System"));
        panel.add(themeCombo, gbc);

        // Preview
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Preview:"), gbc);

        gbc.gridx = 1;
        previewLabel = new JLabel("Sample text with current font");
        previewLabel.setBorder(BorderFactory.createEtchedBorder());
        panel.add(previewLabel, gbc);

        return panel;
    }

    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Auto Backup
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        autoBackupCheck = new JCheckBox("Enable Auto Backup");
        autoBackupCheck.setSelected(Boolean.parseBoolean(getSetting("auto_backup", "true")));
        panel.add(autoBackupCheck, gbc);

        // Backup Interval
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Backup Interval (hours):"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
            Integer.parseInt(getSetting("backup_interval", "24")), 1, 168, 1);
        backupIntervalSpinner = new JSpinner(spinnerModel);
        panel.add(backupIntervalSpinner, gbc);

        // PDF Default Directory
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("PDF Default Directory:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel pdfDirPanel = new JPanel(new BorderLayout(5, 0));
        pdfDirectoryField = new JTextField(getSetting("pdf_default_directory", System.getProperty("user.home")));
        pdfDirectoryField.setPreferredSize(new Dimension(200, 25));

        browsePdfButton = new JButton("Browse");
        browsePdfButton.addActionListener(e -> browsePdfDirectory());

        pdfDirPanel.add(pdfDirectoryField, BorderLayout.CENTER);
        pdfDirPanel.add(browsePdfButton, BorderLayout.EAST);
        panel.add(pdfDirPanel, gbc);

        // Currency
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        panel.add(new JLabel("Currency:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        String[] currencies = {"USD - US Dollar ($)", "EUR - Euro (€)", "GBP - British Pound (£)",
                               "JPY - Japanese Yen (¥)", "CHF - Swiss Franc (Fr)",
                               "CAD - Canadian Dollar ($)", "AUD - Australian Dollar ($)",
                               "CNY - Chinese Yuan (¥)", "INR - Indian Rupee (₹)",
                               "BRL - Brazilian Real (R$)", "MXN - Mexican Peso ($)",
                               "ZAR - South African Rand (R)", "SEK - Swedish Krona (kr)",
                               "NOK - Norwegian Krone (kr)", "DKK - Danish Krone (kr)",
                               "PLN - Polish Zloty (zł)", "RUB - Russian Ruble (₽)",
                               "TRY - Turkish Lira (₺)", "KRW - South Korean Won (₩)",
                               "SGD - Singapore Dollar ($)", "HKD - Hong Kong Dollar ($)",
                               "NZD - New Zealand Dollar ($)", "THB - Thai Baht (฿)"};
        currencyCombo = new JComboBox<>(currencies);
        currencyCombo.setSelectedItem(getSetting("currency", "EUR - Euro (€)"));
        panel.add(currencyCombo, gbc);

        // Default VAT
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Default VAT Rate (%):"), gbc);

        gbc.gridx = 1;
        defaultVatField = new JTextField(getSetting("default_vat", "22.0"));
        defaultVatField.setPreferredSize(new Dimension(200, 25));
        panel.add(defaultVatField, gbc);

        // Add vertical glue to push components to top
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private void browsePdfDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Default PDF Directory");

        String currentDir = pdfDirectoryField.getText();
        if (currentDir != null && !currentDir.trim().isEmpty()) {
            File dir = new File(currentDir);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setCurrentDirectory(dir);
            }
        }

        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        if (fileChooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            pdfDirectoryField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JPanel createCompanyDataPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Company Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("* Company Name:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        companyNameField = new JTextField(25);
        formPanel.add(companyNameField, gbc);

        // VAT Number
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("VAT Number:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        vatNumberField = new JTextField(25);
        formPanel.add(vatNumberField, gbc);

        // Tax Code
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tax Code:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        taxCodeField = new JTextField(25);
        formPanel.add(taxCodeField, gbc);

        // Address
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Address:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        addressField = new JTextField(25);
        formPanel.add(addressField, gbc);

        // City
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        formPanel.add(new JLabel("City:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        cityField = new JTextField(25);
        formPanel.add(cityField, gbc);

        // Postal Code
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Postal Code:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        postalCodeField = new JTextField(25);
        formPanel.add(postalCodeField, gbc);

        // Country
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Country:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        countryField = new JTextField(25);
        countryField.setText("Italy");
        formPanel.add(countryField, gbc);

        // Phone
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Phone:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        phoneField = new JTextField(25);
        formPanel.add(phoneField, gbc);

        // Email
        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        emailField = new JTextField(25);
        formPanel.add(emailField, gbc);

        // Website
        gbc.gridx = 0; gbc.gridy = 9; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Website:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        websiteField = new JTextField(25);
        formPanel.add(websiteField, gbc);

        // Logo Path
        gbc.gridx = 0; gbc.gridy = 10; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Logo Path:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel logoPanel = new JPanel(new BorderLayout(5, 0));
        logoPathField = new JTextField(20);
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseForLogo());
        logoPanel.add(logoPathField, BorderLayout.CENTER);
        logoPanel.add(browseButton, BorderLayout.EAST);
        formPanel.add(logoPanel, gbc);

        // Add some vertical spacing
        gbc.gridx = 0; gbc.gridy = 11; gbc.weighty = 1.0;
        formPanel.add(Box.createVerticalGlue(), gbc);

        // Legend
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        legendPanel.add(new JLabel("* Required fields"));

        panel.add(legendPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(formPanel), BorderLayout.CENTER);

        return panel;
    }

    private void browseForLogo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Image Files", "jpg", "jpeg", "png", "gif", "bmp"));
        fileChooser.setDialogTitle("Select Company Logo");

        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        if (fileChooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            logoPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadCompanyData() {
        CompanyData companyData = CompanyData.getInstance();
        companyNameField.setText(companyData.getCompanyName());
        vatNumberField.setText(companyData.getVatNumber());
        taxCodeField.setText(companyData.getTaxCode());
        addressField.setText(companyData.getAddress());
        cityField.setText(companyData.getCity());
        postalCodeField.setText(companyData.getPostalCode());
        countryField.setText(companyData.getCountry());
        phoneField.setText(companyData.getPhone());
        emailField.setText(companyData.getEmail());
        websiteField.setText(companyData.getWebsite());
        logoPathField.setText(companyData.getLogoPath());
    }

    private boolean saveCompanyData() {
        if (companyNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Company name is required.",
                "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        CompanyData companyData = CompanyData.getInstance();
        companyData.setCompanyName(companyNameField.getText().trim());
        companyData.setVatNumber(vatNumberField.getText().trim());
        companyData.setTaxCode(taxCodeField.getText().trim());
        companyData.setAddress(addressField.getText().trim());
        companyData.setCity(cityField.getText().trim());
        companyData.setPostalCode(postalCodeField.getText().trim());
        companyData.setCountry(countryField.getText().trim());
        companyData.setPhone(phoneField.getText().trim());
        companyData.setEmail(emailField.getText().trim());
        companyData.setWebsite(websiteField.getText().trim());
        companyData.setLogoPath(logoPathField.getText().trim());

        return companyData.saveToDatabase();
    }

    private void updatePreview() {
        int fontSize = fontSizeSlider.getValue();
        Font newFont = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
        previewLabel.setFont(newFont);
        previewLabel.setText("Sample text - Size: " + fontSize);
    }

    private void applyAllSettings() {
        applySettings();
        JOptionPane.showMessageDialog(this,
            "Settings applied successfully!",
            "Settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveAllSettings() {
        boolean success = true;

        try {
            saveAppSettings();
        } catch (Exception e) {
            success = false;
            JOptionPane.showMessageDialog(this,
                "Error saving application settings: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (!saveCompanyData()) {
            success = false;
            JOptionPane.showMessageDialog(this,
                "Error saving company data",
                "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (success) {
            applySettings();
            JOptionPane.showMessageDialog(this,
                "Settings saved successfully!",
                "Settings", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveAppSettings() {
        settings.setProperty("font_size", String.valueOf(fontSizeSlider.getValue()));
        settings.setProperty("theme", (String)themeCombo.getSelectedItem());
        settings.setProperty("auto_backup", String.valueOf(autoBackupCheck.isSelected()));
        settings.setProperty("backup_interval", String.valueOf(backupIntervalSpinner.getValue()));
        settings.setProperty("pdf_default_directory", pdfDirectoryField.getText().trim());
        settings.setProperty("currency", (String)currencyCombo.getSelectedItem());
        settings.setProperty("default_vat", defaultVatField.getText().trim());

        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            settings.store(fos, "Application Settings");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save settings file", e);
        }
    }

    private void loadSettings() {
        try (FileInputStream fis = new FileInputStream(SETTINGS_FILE)) {
            settings.load(fis);
        } catch (IOException e) {
            setDefaultSettings();
        }
    }

    private void setDefaultSettings() {
        settings.setProperty("font_size", "12");
        settings.setProperty("theme", "System");
        settings.setProperty("auto_backup", "true");
        settings.setProperty("backup_interval", "24");
        settings.setProperty("pdf_default_directory", System.getProperty("user.home"));
        settings.setProperty("currency", "EUR - Euro (€)");
        settings.setProperty("default_vat", "22.0");
    }

    private void applyCurrentSettings() {
        updatePreview();
    }

    private void applySettings() {
        int fontSize = Integer.parseInt(getSetting("font_size", "12"));
        updateGlobalFont(fontSize);

        String theme = getSetting("theme", "System");
        updateLookAndFeel(theme);
    }

    private void updateGlobalFont(int size) {
        Font newFont = new Font(Font.SANS_SERIF, Font.PLAIN, size);

        UIManager.put("Label.font", newFont);
        UIManager.put("Button.font", newFont);
        UIManager.put("TextField.font", newFont);
        UIManager.put("Table.font", newFont);
        UIManager.put("Menu.font", newFont);
        UIManager.put("MenuItem.font", newFont);

        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }

    private void updateLookAndFeel(String theme) {
        try {
            switch (theme) {
                case "System":
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
                case "Metal":
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    break;
                case "Nimbus":
                    for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                        if ("Nimbus".equals(info.getName())) {
                            UIManager.setLookAndFeel(info.getClassName());
                            break;
                        }
                    }
                    break;
            }

            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getCurrentFontSize() {
        return Integer.parseInt(getSetting("font_size", "12"));
    }

    private String getSetting(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }

    // Static methods for global access
    public static String getGlobalSetting(String key, String defaultValue) {
        if (settings.isEmpty()) {
            loadGlobalSettings();
        }
        return settings.getProperty(key, defaultValue);
    }

    public static void setGlobalSetting(String key, String value) {
        if (settings.isEmpty()) {
            loadGlobalSettings();
        }
        settings.setProperty(key, value);

        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            settings.store(fos, "Application Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadGlobalSettings() {
        try (FileInputStream fis = new FileInputStream(SETTINGS_FILE)) {
            settings.load(fis);
        } catch (IOException e) {
            // Use defaults
        }
    }

    public static void applyGlobalSettings() {
        int fontSize = Integer.parseInt(getGlobalSetting("font_size", "12"));
        Font newFont = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);

        UIManager.put("Label.font", newFont);
        UIManager.put("Button.font", newFont);
        UIManager.put("TextField.font", newFont);
        UIManager.put("Table.font", newFont);
        UIManager.put("Menu.font", newFont);
        UIManager.put("MenuItem.font", newFont);
    }

    // Utility methods for currency and VAT
    public static String getCurrency() {
        return getGlobalSetting("currency", "EUR - Euro (€)");
    }

    public static String getCurrencySymbol() {
        String currency = getCurrency();
        // Extract the symbol from the currency string (e.g., "EUR - Euro (€)" -> "€")
        int startIdx = currency.indexOf('(');
        int endIdx = currency.indexOf(')');
        if (startIdx != -1 && endIdx != -1) {
            return currency.substring(startIdx + 1, endIdx);
        }
        return "€"; // Default
    }

    public static double getDefaultVatRate() {
        try {
            return Double.parseDouble(getGlobalSetting("default_vat", "22.0"));
        } catch (NumberFormatException e) {
            return 22.0;
        }
    }
}
