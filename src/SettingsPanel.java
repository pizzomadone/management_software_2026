import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Properties;
import java.io.*;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.math.BigDecimal;

public class SettingsPanel extends JPanel {
    private static Properties settings = new Properties();

    private JSlider fontSizeSlider;
    private JComboBox<String> themeCombo;
    private JLabel previewLabel;

    // PDF Settings
    private JTextField pdfDirectoryField;
    private JButton browsePdfButton;

    // Currency and VAT Settings
    private JComboBox<String> currencyCombo;
    private JTextField defaultVatField;
    private JComboBox<String> languageCombo;
    private boolean currencyChangedDuringUpdate;

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

        // PDF Default Directory
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
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
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
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
        for(String choice:currencies) {
            if(choice.startsWith(getCurrencyCode()+" - "))currencyCombo.setSelectedItem(choice);
        }
        panel.add(currencyCombo, gbc);

        // Default VAT
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Default VAT Rate (%):"), gbc);

        gbc.gridx = 1;
        defaultVatField = new JTextField(getSetting("default_vat", "22.0"));
        defaultVatField.setPreferredSize(new Dimension(200, 25));
        panel.add(defaultVatField, gbc);

        // Language
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Language:"), gbc);

        gbc.gridx = 1;
        languageCombo = new JComboBox<>(I18n.languageNames());
        languageCombo.setSelectedItem(I18n.nameForCode(getSetting("language", "it")));
        panel.add(languageCombo, gbc);

        // Add vertical glue to push components to top
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private void browsePdfDirectory() {
        String currentDir = pdfDirectoryField.getText();
        File selectedDirectory = chooseDirectory(
            "Select Default PDF Directory",
            currentDir == null || currentDir.isBlank() ? null : new File(currentDir));
        if (selectedDirectory != null) pdfDirectoryField.setText(selectedDirectory.getAbsolutePath());
    }

    private File chooseDirectory(String title, File initialDirectory) {
        File initial = initialDirectory != null && initialDirectory.isDirectory()
            ? initialDirectory : new File(System.getProperty("user.home", "."));
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            String property = "apple.awt.fileDialogForDirectories";
            String previous = System.getProperty(property);
            System.setProperty(property, "true");
            FileDialog dialog = new FileDialog((Frame) SwingUtilities.getWindowAncestor(this),
                title, FileDialog.LOAD);
            try {
                dialog.setDirectory(initial.getAbsolutePath());
                dialog.setVisible(true);
                return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
            } finally {
                dialog.dispose();
                if (previous == null) System.clearProperty(property);
                else System.setProperty(property, previous);
            }
        }
        JFileChooser chooser = new JFileChooser(initial);
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile() : null;
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
        countryField.setText(I18n.tr("Italy"));
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
        previewLabel.setText(I18n.tr("Sample text - Size") + ": " + fontSize);
    }

    private void applyAllSettings() {
        try {
            updateSettingsFromFields();
            applySettings();
            JOptionPane.showMessageDialog(this,
                I18n.tr("Settings applied successfully!"),
                I18n.tr("Settings"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
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
                I18n.tr("Settings saved successfully!"),
                I18n.tr("Settings"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveAppSettings() {
        updateSettingsFromFields();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(AppConstants.getSettingsPath().toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            settings.store(writer, "Application Settings");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save settings file", e);
        }
    }

    /** Copies every editable application option into the live settings. */
    private void updateSettingsFromFields() {
        currencyChangedDuringUpdate = false;
        double vatRate;
        try {
            vatRate = Numbers.vat(defaultVatField.getText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Default VAT rate must be a number between 0 and 100");
        }
        if (vatRate < 0 || vatRate > 100) {
            throw new IllegalArgumentException("Default VAT rate must be between 0 and 100");
        }

        settings.setProperty("font_size", String.valueOf(fontSizeSlider.getValue()));
        settings.setProperty("theme", (String)themeCombo.getSelectedItem());
        settings.setProperty("pdf_default_directory", pdfDirectoryField.getText().trim());
        String selectedCurrency=(String)currencyCombo.getSelectedItem();
        String requestedCode=DatabaseCurrency.codeFromSetting(selectedCurrency);
        String currentCode=getCurrencyCode();
        if(!requestedCode.equals(currentCode)) {
            selectedCurrency=performCurrencyChange(currentCode,requestedCode,selectedCurrency);
        }
        settings.setProperty("currency", selectedCurrency);
        settings.setProperty("default_vat", String.valueOf(vatRate));
        settings.setProperty("language", I18n.codeForName((String) languageCombo.getSelectedItem()));
    }

    private String performCurrencyChange(String oldCode,String newCode,String selectedCurrency) {
        String warning=I18n.tr("Changing the operating currency affects products, costs, supplier price lists, customer orders, supplier orders and Draft invoices.")+
            "\n\n"+I18n.tr("Issued, Paid and Canceled invoices keep their original currency and amounts.")+
            "\n\n"+I18n.tr("Choose how existing operational values should be handled:");
        Object[] options={
            I18n.tr("Change currency only"),
            I18n.tr("Convert values"),
            I18n.tr("Cancel")
        };
        int choice=JOptionPane.showOptionDialog(this,warning,I18n.tr("Change Currency"),
            JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,options,options[0]);

        if(choice<0 || choice==2) {
            restoreCurrencySelection(oldCode);
            return (String)currencyCombo.getSelectedItem();
        }

        try {
            if(choice==0) {
                String relabel=I18n.tr("No amounts will be converted.")+"\n"+
                    I18n.tr("For example, 100 {OLD} will become 100 {NEW}.")
                        .replace("{OLD}",oldCode).replace("{NEW}",newCode);
                int confirm=JOptionPane.showConfirmDialog(this,relabel,I18n.tr("Confirm Currency Change"),
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
                if(confirm!=JOptionPane.OK_OPTION) {
                    restoreCurrencySelection(oldCode);
                    return (String)currencyCombo.getSelectedItem();
                }
                DatabaseCurrency.change(newCode,DatabaseCurrency.ChangeMode.RELABEL,null);
                currencyChangedDuringUpdate = true;
            } else {
                String input=JOptionPane.showInputDialog(this,
                    I18n.tr("Enter the exchange rate: 1 {OLD} = ? {NEW}")
                        .replace("{OLD}",oldCode).replace("{NEW}",newCode),
                    "1.0");
                if(input==null) {
                    restoreCurrencySelection(oldCode);
                    return (String)currencyCombo.getSelectedItem();
                }
                BigDecimal rate;
                try {
                    rate=new BigDecimal(input.trim().replace(',','.'));
                } catch(NumberFormatException e) {
                    throw new IllegalArgumentException(I18n.tr("Exchange rate must be a valid positive number."));
                }
                if(rate.signum()<=0) throw new IllegalArgumentException(I18n.tr("Exchange rate must be a valid positive number."));

                String confirmation=I18n.tr("Existing operational monetary values will be converted using:")+"\n"+
                    "1 "+oldCode+" = "+rate.stripTrailingZeros().toPlainString()+" "+newCode+"\n\n"+
                    I18n.tr("Issued, Paid and Canceled invoices will not be changed.");
                int confirm=JOptionPane.showConfirmDialog(this,confirmation,I18n.tr("Confirm Currency Conversion"),
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
                if(confirm!=JOptionPane.OK_OPTION) {
                    restoreCurrencySelection(oldCode);
                    return (String)currencyCombo.getSelectedItem();
                }
                DatabaseCurrency.change(newCode,DatabaseCurrency.ChangeMode.CONVERT,rate);
                currencyChangedDuringUpdate = true;
            }
            return selectedCurrency;
        } catch(java.sql.SQLException e) {
            restoreCurrencySelection(oldCode);
            throw new IllegalArgumentException(e.getMessage(),e);
        }
    }

    private void restoreCurrencySelection(String code) {
        for(int i=0;i<currencyCombo.getItemCount();i++) {
            String item=currencyCombo.getItemAt(i);
            if(item!=null && item.startsWith(code+" - ")) {
                currencyCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void loadSettings() {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(AppConstants.getSettingsPath().toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            settings.load(reader);
        } catch (IOException e) {
            setDefaultSettings();
        }
    }

    private void setDefaultSettings() {
        settings.setProperty("font_size", "12");
        settings.setProperty("theme", "System");
        settings.setProperty("pdf_default_directory", System.getProperty("user.home"));
        settings.setProperty("currency", "EUR - Euro (€)");
        settings.setProperty("default_vat", "22.0");
        settings.setProperty("language", "it");
    }

    private void applyCurrentSettings() {
        updatePreview();
    }

    private void applySettings() {
        int fontSize = Integer.parseInt(getSetting("font_size", "12"));
        updateGlobalFont(fontSize);

        String theme = getSetting("theme", "System");
        updateLookAndFeel(theme);
        I18n.setLanguage(getSetting("language", "it"));
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner instanceof MainWindow mainWindow) {
            mainWindow.refreshLocalizedTitle();
            if(currencyChangedDuringUpdate) mainWindow.refreshAfterCurrencyChange();
        }
        currencyChangedDuringUpdate = false;
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

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(AppConstants.getSettingsPath().toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            settings.store(writer, "Application Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadGlobalSettings() {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(AppConstants.getSettingsPath().toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            settings.load(reader);
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
        return getCurrencySymbol(getCurrencyCode());
    }

    public static String getCurrencySymbol(String currencyCode) {
        try { return Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()); }
        catch(IllegalArgumentException e) { return currencyCode; }
    }

    public static String getCurrencyCode() {
        if (DatabaseCurrency.boundCode()!=null) return DatabaseCurrency.boundCode();
        String currency = getCurrency();
        int separatorIndex = currency.indexOf(" - ");
        return separatorIndex > 0 ? currency.substring(0, separatorIndex) : "EUR";
    }

    public static String formatCurrency(double amount) {
        return formatCurrency(amount,getCurrencyCode());
    }

    public static String formatCurrency(double amount,String currencyCode) {
        if(currencyCode==null || currencyCode.isBlank()) currencyCode=getCurrencyCode();
        try {
            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.getDefault());
            Currency currency = Currency.getInstance(currencyCode);
            formatter.setCurrency(currency);
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(2);
            return formatter.format(amount);
        } catch (IllegalArgumentException e) {
            return String.format("%s %.2f", getCurrencySymbol(currencyCode), amount);
        }
    }

    public static double getDefaultVatRate() {
        try {
            return Numbers.vat(getGlobalSetting("default_vat", "22.0"));
        } catch (IllegalArgumentException e) {
            return 22.0;
        }
    }
}
