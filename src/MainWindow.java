/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainWindow extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JToolBar toolBar;
    private JMenuBar menuBar;

    // Toolbar buttons
    private JButton homeButton;
    private JButton webButton;
    private JButton customersButton;
    private JButton productsButton;
    private JButton ordersButton;
    private JButton supplierOrdersButton;
    private JButton invoicesButton;
    private JButton suppliersButton;
    private JButton warehouseButton;
    private JButton salesReportButton;
    private JButton statsButton;
    private JButton warehouseReportButton;
    private JButton backupButton;
    private JButton settingsButton;

    // Active button tracking
    private JButton activeButton = null;
    private Color activeButtonColor = new Color(200, 220, 240); // Light blue/gray

    // Panel instances
    private DashboardPanel dashboardPanel;
    private WebPanel webPanel;
    private CustomersPanel customersPanel;
    private ProductsPanel productsPanel;
    private OrdersPanel ordersPanel;
    private SupplierOrdersPanel supplierOrdersPanel;
    private InvoicesPanel invoicesPanel;
    private SuppliersPanel suppliersPanel;
    private WarehousePanel warehousePanel;
    private SalesReportPanel salesReportPanel;
    private AdvancedStatsPanel advancedStatsPanel;
    private WarehouseReportPanel warehouseReportPanel;
    private BackupPanel backupPanel;
    private SettingsPanel settingsPanel;
    
    // Current panel name for reference
    private String currentPanel = "HOME";
    private boolean databaseReady;
    private Path missingDatabaseAtStartup;
    private JMenuItem newDatabaseItem;
    private JMenuItem openDatabaseItem;
    private JMenuItem backupManagementItem;
    private JMenuItem exitMenuItem;
    
    public MainWindow() {
        SettingsPanel.loadGlobalSettings();
        String language = SettingsPanel.getGlobalSetting("language", "");
        if (language.isBlank()) {
            String selected = (String) Dialogs.showInputDialog(null,
                "Lingua / Idioma / Langue / Sprache / Język",
                "WareStat - Language", JOptionPane.QUESTION_MESSAGE, null,
                I18n.languageNames(), I18n.nameForCode("it"));
            language = I18n.codeForName(selected != null ? selected : I18n.nameForCode("it"));
            SettingsPanel.setGlobalSetting("language", language);
        }
        I18n.setLanguage(language);
        I18n.install();

        // EULA must be accepted again for every app-version or license-text change.
        // The local receipt and native user preferences are outside company .wsbq files.
        if (!EulaAcceptanceStore.isCurrentLicenseAccepted()) {
            boolean accepted = EulaDialog.showDialog(null);
            if (!accepted) {
                System.exit(0);
                return;
            }
        }

        ApplicationLock.acquire();

        // The window remains usable for recovery when a selected company is missing.
        // Do not create, move or replace a database without an explicit action.
        Path selectedAtStartup = AppConstants.getDatabasePath();
        databaseReady = !needsStartupDatabaseRecovery(selectedAtStartup);
        missingDatabaseAtStartup = databaseReady ? null : selectedAtStartup;
        if (databaseReady) {
            if (!initializeStartupDatabase()) {
                System.exit(0);
                return;
            }
            ProductImageStore.getInstance().removeOrphanFiles();
            BackupManager.getInstance().startAutomaticBackups();
        }

        // Ensure license file exists
        LicenseText.ensureLicenseFileExists();

        setupWindow();
        RuntimeNotice.install(this);
        setupMenuBar();
        setupToolBar();
        setupMainPanel();
        setupPanels();

        // Apply global settings on startup
        SettingsPanel.applyGlobalSettings();
        I18n.localize(this);

        setDatabaseAvailability(databaseReady);
        if (databaseReady) SwingUtilities.invokeLater(() -> showPanel("WEB"));
    }
    
    /**
     * No database means recovery-only UI. A genuine 1.2 installation still
     * receives its explicit legacy migration prompt instead.
     */
    static boolean needsStartupDatabaseRecovery(Path selected) {
        return !Files.isRegularFile(selected)
            && !(selected.equals(AppConstants.getDefaultDatabasePath())
                && WareStatLocalUpgrade.needsStartupConfirmation());
    }

    private static boolean initializeStartupDatabase() {
        // Genuine WareStat 1.2 installations still require explicit consent.
        if (WareStatLocalUpgrade.needsStartupConfirmation()) {
            Path legacy = WareStatLocalUpgrade.installedLegacyFile();
            try {
                if (!WareStatLegacy12.isVersion12(legacy))
                    throw new IllegalArgumentException(
                        "The existing data.db is not a recognized WareStat 1.2 database");
                if (!confirmLegacyMigration(legacy)) return false;
                WareStatLocalUpgrade.migrateConfirmed(legacy,
                    WareStatLegacy12.installedCurrency());
            } catch (Exception problem) {
                Dialogs.showMessageDialog(null,
                    I18n.tr("Unable to migrate WareStat 1.2 database") + ":\n"
                        + problem.getMessage(), I18n.tr("Database Error"),
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        DatabaseManager.getInstance().initDatabase();
        return true;
    }

    private void showMissingDatabaseNotice() {
        String notice = missingDatabaseAtStartup.equals(AppConstants.getDefaultDatabasePath())
            ? I18n.tr("No WareStat database was found.")
            : I18n.tr("The previously selected WareStat database cannot be found.");
        Dialogs.showMessageDialog(this,
            notice + "\n" + missingDatabaseAtStartup + "\n\n" +
                I18n.tr("Use File to open or create a database, or Tools to restore a backup."),
            I18n.tr("Database Not Found"), JOptionPane.WARNING_MESSAGE);
    }

    private void setDatabaseAvailability(boolean available) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu == null) continue;
            if (i == 0) {
                menu.setEnabled(true);
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item != null)
                        item.setEnabled(available
                            ? item != newDatabaseItem
                            : item == newDatabaseItem || item == openDatabaseItem
                                || item == exitMenuItem);
                }
            } else if (i == 3) {
                menu.setEnabled(true);
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item != null)
                        item.setEnabled(available || item == backupManagementItem);
                }
            } else {
                menu.setEnabled(available);
            }
        }
        toolBar.setVisible(available);
        menuBar.revalidate();
        revalidate();
        repaint();
    }

    private void activateRecoveredDatabase() {
        databaseReady = true;
        missingDatabaseAtStartup = null;
        ProductImageStore.getInstance().removeOrphanFiles();
        BackupManager.getInstance().startAutomaticBackups();
        setupPanels();
        setDatabaseAvailability(true);
        refreshCompanyPanels();
    }

    /** A failed recovery must never save a new active path or unlock the app. */
    private void openRecoveryDatabase(Path chosen) {
        Path candidate = chosen.toAbsolutePath().normalize();
        try {
            boolean legacy = candidate.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".db");
            if (legacy) {
                if (!WareStatLegacy12.isVersion12(candidate))
                    throw new java.io.IOException("Not a WareStat 1.2 database");
                if (!confirmLegacyMigration(candidate)) return;
                String currency = DatabaseCurrency.confirmLegacyCurrency(this, candidate);
                if (currency == null) return;
                candidate = WareStatLocalUpgrade.migrateConfirmed(candidate, currency);
            } else if (!candidate.getFileName().toString()
                    .toLowerCase(java.util.Locale.ROOT).endsWith(".wsbq")
                    || !Files.isRegularFile(candidate)
                    || !WareStatFileHeader.isSealed(candidate))
                throw new java.io.IOException("Select a valid WareStat .wsbq database");

            AppConstants.useDatabasePathForSession(candidate);
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            AppConstants.rememberDatabasePath(candidate);
            AppConstants.useDatabasePathForSession(null);
            activateRecoveredDatabase();
        } catch (Exception problem) {
            try {
                DatabaseManager.getInstance().shutdownAndSeal();
            } catch (RuntimeException closeError) {
                problem.addSuppressed(closeError);
            } finally {
                AppConstants.useDatabasePathForSession(null);
            }
            showDatabaseError("Unable to open database", problem);
        }
    }

    private void createRecoveryDatabase() {
        Path target = chooseDatabaseFile(true);
        if (target == null) return;
        if (!target.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".wsbq"))
            target = target.resolveSibling(target.getFileName() + ".wsbq");
        target = target.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            Dialogs.showMessageDialog(this,
                I18n.tr("The selected file already exists. Choose another filename."),
                I18n.tr("New Database"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path staging = null;
        try {
            Files.createDirectories(target.getParent());
            staging = Files.createTempFile(target.getParent(), "warestat-new-", ".wsbq");
            DatabaseManager.createEmptyDatabase(staging, SettingsPanel.getCurrencyCode());
            WareStatFileHeader.seal(staging);
            Files.move(staging, target);
            staging = null;
            openRecoveryDatabase(target);
        } catch (Exception problem) {
            showDatabaseError("Unable to create database", problem);
        } finally {
            if (staging != null) {
                try { Files.deleteIfExists(staging); } catch (java.io.IOException ignored) {}
            }
        }
    }

    private void restoreRecoveryBackup(Path source) {
        Path target = chooseDatabaseFile(true);
        if (target == null) return;
        if (!target.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".wsbq"))
            target = target.resolveSibling(target.getFileName() + ".wsbq");
        target = target.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            Dialogs.showMessageDialog(this,
                I18n.tr("The selected file already exists. Choose another filename."),
                I18n.tr("Restore Backup"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String legacyCurrency = null;
            if (!BackupManager.isArchive(source)) {
                legacyCurrency = DatabaseCurrency.confirmLegacyCurrency(this, source);
                if (legacyCurrency == null) return;
            }
            BackupManager.getInstance().restoreBackupAsNewDatabase(source, target, legacyCurrency);
            openRecoveryDatabase(target);
        } catch (Exception problem) {
            showDatabaseError("Unable to restore backup", problem);
        }
    }

    private void setupWindow() {
        setTitle(AppConstants.SOFTWARE_NAME + " - " + I18n.tr("Management System"));
        Image applicationIcon = loadApplicationIcon();
        if (applicationIcon != null) setIconImage(applicationIcon);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        // Add shutdown management to correctly close the database
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                DatabaseManager.getInstance().shutdownAndSeal();
            }
        });
    }

    private static Image loadApplicationIcon() {
        java.net.URL iconUrl = MainWindow.class.getResource("/icons/logo.png");
        return iconUrl == null ? null : new ImageIcon(iconUrl).getImage();
    }

    private static void configureTaskbarIcon() {
        Image icon = loadApplicationIcon();
        if (icon != null && Taskbar.isTaskbarSupported() &&
                Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(icon);
        }
    }
    
    private void setupMenuBar() {
        menuBar = new JMenuBar();
        
        // File Menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem newCustomerItem = new JMenuItem("New Customer");
        newCustomerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newCustomerItem.addActionListener(e -> createNewCustomer());
        
        JMenuItem newProductItem = new JMenuItem("New Product");
        newProductItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newProductItem.addActionListener(e -> createNewProduct());
        
        JMenuItem newOrderItem = new JMenuItem("New Customer Order");
        newOrderItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newOrderItem.addActionListener(e -> createNewOrder());

        JMenuItem newSupplierOrderItem = new JMenuItem("New Supplier Order");
        newSupplierOrderItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newSupplierOrderItem.addActionListener(e -> createNewSupplierOrder());

        JMenuItem newInvoiceItem = new JMenuItem("New Invoice");
        newInvoiceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newInvoiceItem.addActionListener(e -> createNewInvoice());
        
        fileMenu.add(newCustomerItem);
        fileMenu.add(newProductItem);
        fileMenu.add(newOrderItem);
        fileMenu.add(newSupplierOrderItem);
        fileMenu.add(newInvoiceItem);
        fileMenu.addSeparator();

        newDatabaseItem = new JMenuItem("New Database...");
        newDatabaseItem.addActionListener(e -> createRecoveryDatabase());
        fileMenu.add(newDatabaseItem);

        JMenuItem newArchiveItem = new JMenuItem("New Archive...");
        newArchiveItem.addActionListener(e -> createNewArchive());
        fileMenu.add(newArchiveItem);

        openDatabaseItem = new JMenuItem("Open...");
        openDatabaseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openDatabaseItem.addActionListener(e -> openDatabase());
        fileMenu.add(openDatabaseItem);

        JMenuItem saveAsDatabaseItem = new JMenuItem("Save As...");
        saveAsDatabaseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        saveAsDatabaseItem.addActionListener(e -> saveAsDatabase());
        fileMenu.add(saveAsDatabaseItem);
        fileMenu.addSeparator();
        
        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        settingsItem.addActionListener(e -> openSettings());
        fileMenu.add(settingsItem);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitMenuItem = exitItem;
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        // Management Menu
        JMenu managementMenu = new JMenu("Management");
        managementMenu.setMnemonic(KeyEvent.VK_M);
        
        JMenuItem customersItem = new JMenuItem("Customers");
        customersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        customersItem.addActionListener(e -> showPanel("CUSTOMERS"));
        
        JMenuItem productsItem = new JMenuItem("Products");
        productsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        productsItem.addActionListener(e -> showPanel("PRODUCTS"));
        
        JMenuItem ordersItem = new JMenuItem("Customer Orders");
        ordersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        ordersItem.addActionListener(e -> showPanel("ORDERS"));

        JMenuItem supplierOrdersItem = new JMenuItem("Supplier Orders");
        supplierOrdersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_7,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        supplierOrdersItem.addActionListener(e -> showPanel("SUPPLIER_ORDERS"));
        
        JMenuItem invoicesItem = new JMenuItem("Invoices");
        invoicesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_4, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        invoicesItem.addActionListener(e -> showPanel("INVOICES"));
        
        JMenuItem suppliersItem = new JMenuItem("Suppliers");
        suppliersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_5, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        suppliersItem.addActionListener(e -> showPanel("SUPPLIERS"));
        
        JMenuItem warehouseItem = new JMenuItem("Warehouse");
        warehouseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_6, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        warehouseItem.addActionListener(e -> showPanel("WAREHOUSE"));
        
        managementMenu.add(customersItem);
        managementMenu.add(productsItem);
        managementMenu.add(ordersItem);
        managementMenu.add(supplierOrdersItem);
        managementMenu.add(invoicesItem);
        managementMenu.addSeparator();
        managementMenu.add(suppliersItem);
        managementMenu.add(warehouseItem);
        
        // Reports Menu
        JMenu reportsMenu = new JMenu("Reports");
        reportsMenu.setMnemonic(KeyEvent.VK_R);
        
        JMenuItem salesReportItem = new JMenuItem("Sales Report");
        salesReportItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        salesReportItem.addActionListener(e -> showPanel("SALES_REPORT"));
        
        JMenuItem advancedStatsItem = new JMenuItem("Advanced Statistics");
        advancedStatsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        advancedStatsItem.addActionListener(e -> showPanel("ADVANCED_STATS"));
        
        JMenuItem warehouseReportItem = new JMenuItem("Warehouse Report");
        warehouseReportItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        warehouseReportItem.addActionListener(e -> showPanel("WAREHOUSE_REPORT"));
        
        reportsMenu.add(salesReportItem);
        reportsMenu.add(advancedStatsItem);
        reportsMenu.add(warehouseReportItem);
        
        // Tools Menu
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic(KeyEvent.VK_T);
        
        JMenuItem backupItem = new JMenuItem("Backup Management");
        backupManagementItem = backupItem;
        backupItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        backupItem.addActionListener(e -> showPanel("BACKUP"));
        
        JMenuItem performBackupItem = new JMenuItem("Perform Backup Now");
        performBackupItem.addActionListener(e -> performQuickBackup());
        
        toolsMenu.add(backupItem);
        toolsMenu.add(performBackupItem);
        
        // View Menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        
        JMenuItem homeItem = new JMenuItem("Dashboard");
        int menuShortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        // Cmd+H hides applications on macOS; use Cmd+0 for Dashboard there.
        int homeKey = (menuShortcutMask & KeyEvent.META_DOWN_MASK) != 0
            ? KeyEvent.VK_0 : KeyEvent.VK_H;
        homeItem.setAccelerator(KeyStroke.getKeyStroke(homeKey, menuShortcutMask));
        homeItem.addActionListener(e -> showPanel("HOME"));

        JMenuItem webItem = new JMenuItem("Discover");
        webItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_8,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        webItem.addActionListener(e -> showPanel("WEB"));
        
        JCheckBoxMenuItem showToolbarItem = new JCheckBoxMenuItem("Show Toolbar", true);
        showToolbarItem.addActionListener(e -> toggleToolbar(showToolbarItem.isSelected()));
        
        viewMenu.add(homeItem);
        viewMenu.add(webItem);
        viewMenu.addSeparator();
        viewMenu.add(showToolbarItem);
        
        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        
        JMenuItem aboutItem = new JMenuItem("About " + AppConstants.SOFTWARE_NAME);
        aboutItem.addActionListener(e -> showAboutDialog());
        
        JMenuItem keyboardShortcutsItem = new JMenuItem("Keyboard Shortcuts");
        keyboardShortcutsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        keyboardShortcutsItem.addActionListener(e -> showKeyboardShortcuts());
        
        helpMenu.add(keyboardShortcutsItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);
        
        // Add all menus to menu bar
        menuBar.add(fileMenu);
        menuBar.add(managementMenu);
        menuBar.add(reportsMenu);
        menuBar.add(toolsMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        
        setJMenuBar(menuBar);
    }
    
    private void setupToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);

        // Home button
        homeButton = createToolBarButton("🏠", "Home", "Go to Dashboard");
        homeButton.addActionListener(e -> showPanel("HOME"));
        toolBar.add(homeButton);

        webButton = createToolBarButton("🌐", "Discover", "WareStat news and communications");
        webButton.addActionListener(e -> showPanel("WEB"));
        toolBar.add(webButton);

        toolBar.addSeparator();

        // Management buttons
        customersButton = createToolBarButton("👥", "Customers", "Customer Management");
        customersButton.addActionListener(e -> showPanel("CUSTOMERS"));
        toolBar.add(customersButton);

        productsButton = createToolBarButton("📦", "Products", "Product Management");
        productsButton.addActionListener(e -> showPanel("PRODUCTS"));
        toolBar.add(productsButton);

        ordersButton = createToolBarButton("📝", "Customer Orders", "Customer Order Management");
        ordersButton.addActionListener(e -> showPanel("ORDERS"));
        toolBar.add(ordersButton);

        supplierOrdersButton = createToolBarButton("🚚", "Supplier Orders", "Supplier Order Management");
        supplierOrdersButton.addActionListener(e -> showPanel("SUPPLIER_ORDERS"));
        toolBar.add(supplierOrdersButton);

        invoicesButton = createToolBarButton("📋", "Invoices", "Invoice Management");
        invoicesButton.addActionListener(e -> showPanel("INVOICES"));
        toolBar.add(invoicesButton);

        suppliersButton = createToolBarButton("🏭", "Suppliers", "Supplier Management");
        suppliersButton.addActionListener(e -> showPanel("SUPPLIERS"));
        toolBar.add(suppliersButton);

        warehouseButton = createToolBarButton("🏪", "Warehouse", "Warehouse Management");
        warehouseButton.addActionListener(e -> showPanel("WAREHOUSE"));
        toolBar.add(warehouseButton);

        toolBar.addSeparator();

        // Reports buttons
        salesReportButton = createToolBarButton("📊", "Sales Report", "Sales Analysis");
        salesReportButton.addActionListener(e -> showPanel("SALES_REPORT"));
        toolBar.add(salesReportButton);

        statsButton = createToolBarButton("📈", "Statistics", "Advanced Statistics");
        statsButton.addActionListener(e -> showPanel("ADVANCED_STATS"));
        toolBar.add(statsButton);

        warehouseReportButton = createToolBarButton("📋", "Warehouse Report", "Warehouse Analysis");
        warehouseReportButton.addActionListener(e -> showPanel("WAREHOUSE_REPORT"));
        toolBar.add(warehouseReportButton);

        toolBar.addSeparator();

        // Tools buttons
        backupButton = createToolBarButton("💾", "Backup", "Backup Management");
        backupButton.addActionListener(e -> showPanel("BACKUP"));
        toolBar.add(backupButton);

        settingsButton = createToolBarButton("⚙", "Settings", "Application Settings");
        settingsButton.addActionListener(e -> openSettings());
        toolBar.add(settingsButton);

        add(toolBar, BorderLayout.NORTH);

        // Set home button as active by default
        setActiveButton(homeButton);
    }
    
    private JButton createToolBarButton(String icon, String text, String tooltip) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        
        // Icon
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Text
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        button.add(iconLabel, BorderLayout.CENTER);
        button.add(textLabel, BorderLayout.SOUTH);
        
        button.setToolTipText(tooltip);
        int buttonWidth = Math.max(80, textLabel.getFontMetrics(textLabel.getFont()).stringWidth(text) + 20);
        button.setPreferredSize(new Dimension(buttonWidth, 60));
        textLabel.addPropertyChangeListener("text", event -> {
            int translatedWidth = Math.max(80,
                textLabel.getFontMetrics(textLabel.getFont()).stringWidth(textLabel.getText()) + 20);
            button.setPreferredSize(new Dimension(translatedWidth, 60));
            if (toolBar != null) toolBar.revalidate();
        });
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);

        // Hover effects - only apply if not active button
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button != activeButton) {
                    button.setContentAreaFilled(true);
                    button.setBackground(new Color(230, 230, 230));
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button != activeButton) {
                    button.setContentAreaFilled(false);
                }
            }
        });

        return button;
    }

    /**
     * Sets the active button in the toolbar and removes active state from previous button
     */
    private void setActiveButton(JButton button) {
        // Remove active state from previous button
        if (activeButton != null) {
            activeButton.setContentAreaFilled(false);
            activeButton.setBackground(null);
        }

        // Set new active button
        activeButton = button;
        if (activeButton != null) {
            activeButton.setContentAreaFilled(true);
            activeButton.setBackground(activeButtonColor);
        }
    }

    /**
     * Get the button associated with a panel name
     */
    private JButton getButtonForPanel(String panelName) {
        return switch (panelName) {
            case "HOME" -> homeButton;
            case "WEB" -> webButton;
            case "CUSTOMERS" -> customersButton;
            case "PRODUCTS" -> productsButton;
            case "ORDERS" -> ordersButton;
            case "SUPPLIER_ORDERS" -> supplierOrdersButton;
            case "INVOICES" -> invoicesButton;
            case "SUPPLIERS" -> suppliersButton;
            case "WAREHOUSE" -> warehouseButton;
            case "SALES_REPORT" -> salesReportButton;
            case "ADVANCED_STATS" -> statsButton;
            case "WAREHOUSE_REPORT" -> warehouseReportButton;
            case "BACKUP" -> backupButton;
            case "SETTINGS" -> settingsButton;
            default -> null;
        };
    }
    
    private void setupMainPanel() {
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        if (databaseReady) {
            dashboardPanel = new DashboardPanel(this);
            dashboardPanel.setName("HOME");
            mainPanel.add(dashboardPanel, "HOME");
        } else {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setName("NO_DATABASE");
            JPanel text = new JPanel(new BorderLayout(0, 14));
            JLabel title = new JLabel(I18n.tr("No database open"));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
            text.add(title, BorderLayout.NORTH);
            JTextArea instructions = new JTextArea(
                I18n.tr("Use File to open or create a database, or Tools to restore a backup."));
            instructions.setEditable(false);
            instructions.setOpaque(false);
            instructions.setLineWrap(true);
            instructions.setWrapStyleWord(true);
            instructions.setFocusable(false);
            instructions.setPreferredSize(new Dimension(440, 70));
            text.add(instructions, BorderLayout.CENTER);
            empty.add(text);
            mainPanel.add(empty, "NO_DATABASE");
            currentPanel = "NO_DATABASE";
            cardLayout.show(mainPanel, "NO_DATABASE");
        }

        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupPanels() {
        if (!databaseReady || webPanel != null) return;
        // Discover is the startup page: create its browser eagerly so that the
        // configured URL starts loading even before the card is made visible.
        // The remaining management panels stay lazy to preserve startup cost.
        webPanel = new WebPanel();
        webPanel.setName("WEB");
        mainPanel.add(webPanel, "WEB");
    }
    
    // Menu action methods
    private void createNewCustomer() {
        showPanel("CUSTOMERS");
        // After showing the panel, trigger the add customer dialog
        SwingUtilities.invokeLater(() -> {
            if (customersPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(customersPanel);
                CustomerDialog dialog = new CustomerDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
                if (dialog.isCustomerSaved()) customersPanel.refreshData();
            }
        });
    }
    
    private void createNewProduct() {
        showPanel("PRODUCTS");
        SwingUtilities.invokeLater(() -> {
            if (productsPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(productsPanel);
                ProductDialog dialog = new ProductDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
                if (dialog.isProductSaved()) {
                    productsPanel.loadProducts();
                    refreshDashboard();
                }
            }
        });
    }
    
    public void createNewOrder() {
        showPanel("ORDERS");
        SwingUtilities.invokeLater(() -> {
            if (ordersPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(ordersPanel);
                OrderDialog dialog = new OrderDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
                if (dialog.isOrderSaved()) {
                    Integer savedOrderId = dialog.getSavedOrderId();
                    if (savedOrderId != null) ordersPanel.selectOrderById(savedOrderId);
                    else ordersPanel.refreshData();
                    refreshProductsPanel();
                }
            }
        });
    }
    
    private void createNewSupplierOrder() {
        showPanel("SUPPLIER_ORDERS");
        SwingUtilities.invokeLater(() -> {
            if (supplierOrdersPanel != null) supplierOrdersPanel.createNewSupplierOrder();
        });
    }

    public void createNewInvoice() {
        showPanel("INVOICES");
        SwingUtilities.invokeLater(() -> {
            if (invoicesPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(invoicesPanel);
                InvoiceDialog dialog = new InvoiceDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
                if (dialog.isInvoiceSaved()) {
                    invoicesPanel.loadInvoices();
                    if (ordersPanel != null) ordersPanel.refreshData();
                    refreshProductsPanel();
                }
            }
        });
    }
    
    private void performQuickBackup() {
        try {
            BackupManager.getInstance().performBackup();
            Dialogs.showMessageDialog(this,
                "Backup completed successfully!",
                "Backup",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Dialogs.showMessageDialog(this,
                "Error during backup: " + e.getMessage(),
                "Backup Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void toggleToolbar(boolean visible) {
        toolBar.setVisible(visible);
        revalidate();
        repaint();
    }
    
    private void showAboutDialog() {
        AboutDialog.showDialog(this);
    }
    
    private void showKeyboardShortcuts() {
        // Read directly from the active menu accelerators so Help never
        // lists shortcuts that are missing or forgets newly added ones.
        StringBuilder shortcutsText = new StringBuilder();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu == null) continue;
            StringBuilder section = new StringBuilder();
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                if (item == null || item.getAccelerator() == null) continue;
                section.append(String.format("%-18s %s%n",
                    describeShortcut(item.getAccelerator()), item.getText()));
            }
            if (section.length() == 0) continue;
            if (shortcutsText.length() > 0) shortcutsText.append("\n");
            shortcutsText.append(menu.getText()).append(":\n").append(section);
        }

        JTextArea textArea = new JTextArea(shortcutsText.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(510, 430));

        Dialogs.showMessageDialog(this,
            scrollPane,
            I18n.tr("Keyboard Shortcuts"),
            JOptionPane.INFORMATION_MESSAGE);
    }

    private static String describeShortcut(KeyStroke shortcut) {
        int modifiers = shortcut.getModifiers();
        StringBuilder result = new StringBuilder();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) result.append("Ctrl+");
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) result.append("Cmd+");
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) result.append("Alt+");
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) result.append("Shift+");
        result.append(shortcut.getKeyCode() == KeyEvent.VK_COMMA
            ? "," : KeyEvent.getKeyText(shortcut.getKeyCode()));
        return result.toString();
    }

    private void showPanel(String panelName) {
        if (!databaseReady && !"BACKUP".equals(panelName)) return;
        try {
            // Every management panel loads its initial data in its
            // constructor. Refreshing it again here issued duplicate SQL
            // queries and repopulated all tables on their very first display.
            boolean firstDisplay = !isPanelCreated(panelName);
            if (firstDisplay) {
                createPanel(panelName);
            }

            // Switch to the panel
            cardLayout.show(mainPanel, panelName);
            currentPanel = panelName;

            if (!firstDisplay) {
                // Refresh dashboard data when returning to HOME
                if ("HOME".equals(panelName) && dashboardPanel != null) {
                    dashboardPanel.loadData();
                }
                if ("SUPPLIER_ORDERS".equals(panelName) && supplierOrdersPanel != null) {
                    supplierOrdersPanel.loadOrders();
                }
                if ("BACKUP".equals(panelName) && backupPanel != null) {
                    backupPanel.refreshBackups();
                }
    
                switch (panelName) {
                    case "WAREHOUSE": if (warehousePanel!=null) warehousePanel.refreshData(); break;
                    case "WAREHOUSE_REPORT": if (warehouseReportPanel!=null) warehouseReportPanel.refreshData(); break;
                    case "SALES_REPORT": if (salesReportPanel!=null) salesReportPanel.refreshData(); break;
                    case "ADVANCED_STATS": if (advancedStatsPanel!=null) advancedStatsPanel.refreshData(); break;
                    case "PRODUCTS": if (productsPanel!=null) productsPanel.loadProducts(); break;
                    case "ORDERS": if (ordersPanel!=null) ordersPanel.refreshData(); break;
                    case "INVOICES": if (invoicesPanel!=null) invoicesPanel.loadInvoices(); break;
                    case "CUSTOMERS": if (customersPanel!=null) customersPanel.refreshData(); break;
                    case "SUPPLIERS": if (suppliersPanel!=null) suppliersPanel.refreshData(); break;
                    default: break;
                }
    
            }

            // Refresh localization after data reloads, which may replace dynamic labels/table values.
            Component activePanel = java.util.Arrays.stream(mainPanel.getComponents())
                .filter(component -> panelName.equals(component.getName()))
                .findFirst().orElse(null);
            if (activePanel != null) I18n.localize(activePanel);

            // Update window title
            updateWindowTitle(panelName);

            // Update active button
            JButton button = getButtonForPanel(panelName);
            if (button != null) {
                setActiveButton(button);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error opening " + panelName + ": " + e.getMessage(),
                AppConstants.SOFTWARE_NAME + " - Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private boolean isPanelCreated(String panelName) {
        for (Component comp : mainPanel.getComponents()) {
            if (panelName.equals(comp.getName())) {
                return true;
            }
        }
        return false;
    }
    
    private void createPanel(String panelName) {
        JPanel panel = null;
        
        switch (panelName) {
            case "HOME":
                if (dashboardPanel == null) {
                    dashboardPanel = new DashboardPanel(this);
                }
                panel = dashboardPanel;
                break;
            case "WEB":
                if (webPanel == null) {
                    webPanel = new WebPanel();
                }
                panel = webPanel;
                break;
            case "CUSTOMERS":
                if (customersPanel == null) {
                    customersPanel = new CustomersPanel();
                }
                panel = customersPanel;
                break;
                
            case "PRODUCTS":
                if (productsPanel == null) {
                    productsPanel = new ProductsPanel();
                }
                panel = productsPanel;
                break;
                
            case "ORDERS":
                if (ordersPanel == null) {
                    ordersPanel = new OrdersPanel();
                }
                panel = ordersPanel;
                break;

            case "SUPPLIER_ORDERS":
                if (supplierOrdersPanel == null) {
                    supplierOrdersPanel = new SupplierOrdersPanel();
                }
                panel = supplierOrdersPanel;
                break;
                
            case "INVOICES":
                if (invoicesPanel == null) {
                    invoicesPanel = new InvoicesPanel();
                }
                panel = invoicesPanel;
                break;
                
            case "SUPPLIERS":
                if (suppliersPanel == null) {
                    suppliersPanel = new SuppliersPanel();
                }
                panel = suppliersPanel;
                break;
                
            case "WAREHOUSE":
                if (warehousePanel == null) {
                    warehousePanel = new WarehousePanel();
                }
                panel = warehousePanel;
                break;
                
            case "SALES_REPORT":
                if (salesReportPanel == null) {
                    salesReportPanel = new SalesReportPanel();
                }
                panel = salesReportPanel;
                break;
                
            case "ADVANCED_STATS":
                if (advancedStatsPanel == null) {
                    advancedStatsPanel = new AdvancedStatsPanel();
                }
                panel = advancedStatsPanel;
                break;
                
            case "WAREHOUSE_REPORT":
                if (warehouseReportPanel == null) {
                    warehouseReportPanel = new WarehouseReportPanel();
                }
                panel = warehouseReportPanel;
                break;
                
            case "BACKUP":
                if (backupPanel == null) {
                    backupPanel = databaseReady ? new BackupPanel()
                        : new BackupPanel(true, this::restoreRecoveryBackup);
                }
                panel = backupPanel;
                break;
                
            case "SETTINGS":
                if (settingsPanel == null) {
                    settingsPanel = new SettingsPanel();
                }
                panel = settingsPanel;
                break;
        }
        
        if (panel != null) {
            panel.setName(panelName);
            mainPanel.add(panel, panelName);
            I18n.localize(panel);
        }
    }
    
    private void updateWindowTitle(String panelName) {
        String titleKey = switch (panelName) {
            case "HOME" -> "Dashboard";
            case "WEB" -> "Discover";
            case "CUSTOMERS" -> "Customer Management";
            case "PRODUCTS" -> "Product Catalog";
            case "ORDERS" -> "Customer Order Management";
            case "SUPPLIER_ORDERS" -> "Supplier Order Management";
            case "INVOICES" -> "Invoice Management";
            case "SUPPLIERS" -> "Supplier Management";
            case "WAREHOUSE" -> "Warehouse Management";
            case "SALES_REPORT" -> "Sales Report";
            case "ADVANCED_STATS" -> "Advanced Statistics";
            case "WAREHOUSE_REPORT" -> "Warehouse Report";
            case "BACKUP" -> "Backup Management";
            case "SETTINGS" -> "Settings";
            default -> "Management System";
        };
        setTitle(AppConstants.SOFTWARE_NAME + " - " + I18n.tr(titleKey));
    }

    void refreshLocalizedTitle() {
        updateWindowTitle(currentPanel);
    }
    
    private void openSettings() {
        showPanel("SETTINGS");
    }

    private void createNewArchive() {
        Path destination = chooseNewArchiveFile();
        if (destination == null) return;
        if (!destination.getFileName().toString().toLowerCase().endsWith(".wsbq")) {
            destination = destination.resolveSibling(destination.getFileName() + ".wsbq");
        }

        if (Files.exists(destination)) {
            int replace = Dialogs.showConfirmDialog(this,
                I18n.tr("A file with this name already exists. Replace it?"),
                I18n.tr("Confirm Replacement"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (replace != JOptionPane.YES_OPTION) return;
        }

        String message = I18n.tr(
            "The current data will be saved in the selected archive. WareStat will then start with a new empty database. Continue?")
            + "\n\n" + destination.toAbsolutePath();
        int confirm = Dialogs.showConfirmDialog(this, message,
            I18n.tr("New Archive"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            Path archive = BackupManager.getInstance().archiveAndStartNew(destination);
            Dialogs.showMessageDialog(this,
                I18n.tr("Archive created successfully.") + "\n" + archive
                    + "\n\n" + I18n.tr("A new empty database has been created.")
                    + "\n" + I18n.tr("WareStat will now close. Reopen it to continue."),
                I18n.tr("New Archive"), JOptionPane.INFORMATION_MESSAGE);
            dispose();
            System.exit(0);
        } catch (RuntimeException exception) {
            showDatabaseError("Unable to create new archive", exception);
        }
    }

    private Path chooseNewArchiveFile() {
        String defaultName = "WareStat-Archive-"
            + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".wsbq";
        Path initial = Path.of(BackupManager.getInstance().getArchiveDirectory()).toAbsolutePath();

        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            FileDialog dialog = new FileDialog(this, I18n.tr("New Archive"), FileDialog.SAVE);
            if (Files.isDirectory(initial)) dialog.setDirectory(initial.toString());
            dialog.setFile(defaultName);
            dialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".wsbq"));
            dialog.setVisible(true);
            return dialog.getFile() == null ? null
                : new File(dialog.getDirectory(), dialog.getFile()).toPath();
        }

        JFileChooser chooser = new JFileChooser(Files.isDirectory(initial)
            ? initial.toFile() : AppConstants.getBackupDirectory().toFile());
        chooser.setDialogTitle(I18n.tr("New Archive"));
        chooser.setFileFilter(new FileNameExtensionFilter(
            I18n.tr("WareStat Archive (*.wsbq)"), "wsbq"));
        chooser.setSelectedFile(new File(defaultName));
        return chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile().toPath() : null;
    }

    /** One explicit yes/no prompt, reused by startup and File > Open. */
    private static boolean confirmLegacyMigration(Path source) {
        String message = I18n.tr(
            "The following file was created with WareStat 1.2. Migrate to version 1.3?")
            + "\n" + I18n.tr("Migration may fail.")
            + "\n" + I18n.tr("Make a manual copy before proceeding.")
            + "\n" + I18n.tr("Continue?");
        return Dialogs.showConfirmDialog(null,message,I18n.tr("WareStat 1.2 Migration"),
            JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Open a current .wsbq directly, or migrate a .db only after confirmation. */
    private void openDatabase() {
        Path source = chooseDatabaseFile(false);
        if (source == null) return;
        source = source.toAbsolutePath().normalize();
        if (!databaseReady) {
            openRecoveryDatabase(source);
            return;
        }
        if (source.equals(AppConstants.getDatabasePath().toAbsolutePath().normalize()))
            return;
        boolean legacy = source.getFileName().toString().toLowerCase(
            java.util.Locale.ROOT).endsWith(".db");
        try {
            if (legacy) {
                if (!WareStatLegacy12.isVersion12(source))
                    throw new IllegalArgumentException("Not a WareStat 1.2 database");
                if (!confirmLegacyMigration(source)) return;
            } else if (!Files.isRegularFile(source) || !WareStatFileHeader.isSealed(source)) {
                throw new IllegalArgumentException("Select a valid WareStat 1.3 .wsbq database");
            }
        } catch (Exception problem) {
            showDatabaseError("The selected file is not a valid WareStat database.",problem);
            return;
        }

        Object[] choices = {
            I18n.tr("Save a Copy and Open"),
            I18n.tr("Open Without Saving"),
            I18n.tr("Cancel")
        };
        int choice = Dialogs.showOptionDialog(this,
            I18n.tr("Save a copy of the current database before opening another?"),
            I18n.tr("Open"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, choices, choices[0]);
        Path target;
        try {
            target = legacy ? WareStatLocalUpgrade.upgradedPath(source) : source;
        } catch (Exception problem) {
            showDatabaseError("Unable to migrate WareStat 1.2 database",problem);
            return;
        }
        if (choice == 0 && saveDatabaseCopy(target) == null) return;
        if (choice != 0 && choice != 1) return;

        try {
            if (legacy) {
                String currency = DatabaseCurrency.confirmLegacyCurrency(this,source);
                if (currency == null) return;
                target = WareStatLocalUpgrade.migrateConfirmed(source,currency);
            }
            DatabaseManager.getInstance().openDatabase(target);
            refreshCompanyPanels();
        } catch (Exception problem) {
            showDatabaseError(legacy ? "Unable to migrate WareStat 1.2 database"
                : "Unable to open database",problem);
        }
    }

    /** Save As is an exact, complete company snapshot including all JPEG BLOBs. */
    private void saveAsDatabase() {
        Path copy = saveDatabaseCopy();
        if (copy == null) return;
        try {
            DatabaseManager.getInstance().openDatabase(copy);
            refreshCompanyPanels();
        } catch (Exception problem) {
            showDatabaseError("Database saved, but unable to open the saved copy", problem);
        }
    }

    /**
     * Only an explicit Save As (or the user's confirmation in Open) copies data.
     * The existing file is never modified or replaced by the save operation.
     */
    private Path saveDatabaseCopy() {
        return saveDatabaseCopy(null);
    }

    private Path saveDatabaseCopy(Path databaseBeingOpened) {
        Path destination = chooseDatabaseFile(true);
        if (destination == null) return null;
        if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".wsbq"))
            destination = destination.resolveSibling(destination.getFileName() + ".wsbq");
        destination = destination.toAbsolutePath().normalize();
        if (databaseBeingOpened != null &&
                destination.equals(databaseBeingOpened.toAbsolutePath().normalize())) {
            Dialogs.showMessageDialog(this,
                I18n.tr("The selected save filename is the database being opened. Choose another filename."),
                I18n.tr("Save As"), JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (destination.equals(AppConstants.getDatabasePath().toAbsolutePath().normalize())) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Choose a different filename; the current database saves automatically."),
                I18n.tr("Save As"), JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (Files.exists(destination)) {
            int replace = Dialogs.showConfirmDialog(this,
                I18n.tr("A file with this name already exists. Replace it?"),
                I18n.tr("Confirm Replacement"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (replace != JOptionPane.YES_OPTION) return null;
        }
        try {
            return BackupManager.getInstance().exportDatabase(destination, true);
        } catch (RuntimeException problem) {
            showDatabaseError("Unable to save database", problem);
            return null;
        }
    }

    /**
     * Old company-specific panels must not retain cached objects from the
     * previously opened database. The shared news browser is not company data.
     */
    private void refreshCompanyPanels() {
        mainPanel.removeAll();
        dashboardPanel = null;
        customersPanel = null;
        productsPanel = null;
        ordersPanel = null;
        supplierOrdersPanel = null;
        invoicesPanel = null;
        suppliersPanel = null;
        warehousePanel = null;
        salesReportPanel = null;
        advancedStatsPanel = null;
        warehouseReportPanel = null;
        backupPanel = null;
        settingsPanel = null;
        if (webPanel != null) mainPanel.add(webPanel, "WEB");
        currentPanel = "HOME";
        showPanel("HOME");
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private Path chooseDatabaseFile(boolean save) {
        String defaultName = "WareStat_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".wsbq";
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            FileDialog dialog = new FileDialog(this,
                    I18n.tr(save ? "Save WareStat Database" : "Select a WareStat Database"),
                    save ? FileDialog.SAVE : FileDialog.LOAD);
            if (save) dialog.setFile(defaultName);
            dialog.setFilenameFilter((directory, name) -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                return lower.endsWith(".wsbq") || (!save && lower.endsWith(".db"));
            });
            dialog.setVisible(true);
            return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile()).toPath();
        }
        Path initialFolder = AppConstants.getDatabasePath().getParent();
        if (initialFolder == null || !Files.isDirectory(initialFolder))
            initialFolder = AppConstants.getAppDataDirectory();
        JFileChooser chooser = new JFileChooser(initialFolder.toFile());
        chooser.setDialogTitle(I18n.tr(save ? "Save WareStat Database" : "Select a WareStat Database"));
        chooser.setFileFilter(save
            ? new FileNameExtensionFilter(I18n.tr("WareStat Database (*.wsbq)"), "wsbq")
            : new FileNameExtensionFilter(I18n.tr("WareStat files (*.wsbq, *.db)"),
                "wsbq", "db"));
        if (save) chooser.setSelectedFile(new File(defaultName));
        int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private void showDatabaseError(String message, Exception exception) {
        Dialogs.showMessageDialog(this, I18n.tr(message) + ":\n" + exception.getMessage(),
                I18n.tr("Database Error"), JOptionPane.ERROR_MESSAGE);
    }

    /** Rebuild monetary views after the operating currency changes. */
    public void refreshAfterCurrencyChange() {
        if (dashboardPanel != null) { mainPanel.remove(dashboardPanel); dashboardPanel = null; }
        if (productsPanel != null) { mainPanel.remove(productsPanel); productsPanel = null; }
        if (ordersPanel != null) { mainPanel.remove(ordersPanel); ordersPanel = null; }
        if (supplierOrdersPanel != null) { mainPanel.remove(supplierOrdersPanel); supplierOrdersPanel = null; }
        if (invoicesPanel != null) { mainPanel.remove(invoicesPanel); invoicesPanel = null; }
        if (warehousePanel != null) { mainPanel.remove(warehousePanel); warehousePanel = null; }
        if (salesReportPanel != null) { mainPanel.remove(salesReportPanel); salesReportPanel = null; }
        if (advancedStatsPanel != null) { mainPanel.remove(advancedStatsPanel); advancedStatsPanel = null; }
        if (warehouseReportPanel != null) { mainPanel.remove(warehouseReportPanel); warehouseReportPanel = null; }
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * Refresh ProductsPanel to update stock levels after order changes
     */
    public void refreshProductsPanel() {
        if (productsPanel != null) {
            productsPanel.loadProducts();
        }
        // Also refresh dashboard as stock changes affect KPIs
        refreshDashboard();
    }

    public void openInvoice(String invoiceNumber) {
        showPanel("INVOICES");
        if (invoicesPanel != null) invoicesPanel.selectInvoiceByNumber(invoiceNumber);
    }

    /**
     * Refresh InvoicesPanel to update invoices list after generation
     */
    public void refreshInvoicesPanel() {
        if (invoicesPanel != null) {
            invoicesPanel.loadInvoices();
        }
        // Also refresh dashboard as invoices affect revenue KPI
        refreshDashboard();
    }

    /**
     * Public method to allow other components (like DashboardPanel) to navigate to panels
     */
    public void showPanelByName(String panelName) {
        showPanel(panelName);
    }

    /**
     * Refresh DashboardPanel to update all dashboard data
     */
    public void refreshDashboard() {
        if (dashboardPanel != null) {
            dashboardPanel.loadData();
        }
    }

    /**
     * Navigate to Orders panel and select/highlight a specific order
     * @param orderId The ID of the order to highlight
     */
    public void showOrdersAndSelect(int orderId) {
        // First navigate to the Orders panel
        showPanel("ORDERS");

        // Then select the specific order (after a small delay to ensure panel is visible)
        SwingUtilities.invokeLater(() -> {
            if (ordersPanel != null) {
                ordersPanel.selectOrderById(orderId);
            }
        });
    }

    public void showProductsAndSelect(String productCode) {
        // First navigate to the Products panel
        showPanel("PRODUCTS");

        // Then select the specific product (after a small delay to ensure panel is visible)
        SwingUtilities.invokeLater(() -> {
            if (productsPanel != null) {
                productsPanel.selectProductByCode(productCode);
            }
        });
    }

    public static void main(String[] args) {
        // Add this code block for macOS integration
        final String os = System.getProperty("os.name");
        if (os != null && os.startsWith("Mac OS X")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", AppConstants.SOFTWARE_NAME);
        }

        SwingUtilities.invokeLater(() -> {
            configureTaskbarIcon();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                MainWindow mainWindow = new MainWindow();
                mainWindow.setVisible(true);
                if (!mainWindow.databaseReady) mainWindow.showMissingDatabaseNotice();
                CostIntegrity.Audit audit=DatabaseManager.getInstance().takeStartupCostAudit();
                if (audit != null) Dialogs.showMessageDialog(mainWindow,
                    CostIntegrity.summary(audit), I18n.tr("Database Updated"), JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException error) {
                Dialogs.showMessageDialog(null,error.getMessage(),"WareStat",JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}

