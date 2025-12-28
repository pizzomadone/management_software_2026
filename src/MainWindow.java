import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class MainWindow extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JToolBar toolBar;
    private JMenuBar menuBar;

    // Toolbar buttons
    private JButton homeButton;
    private JButton customersButton;
    private JButton productsButton;
    private JButton ordersButton;
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
    private CustomersPanel customersPanel;
    private ProductsPanel productsPanel;
    private OrdersPanel ordersPanel;
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
    
    public MainWindow() {
        // Initialize the database FIRST before any UI setup
        DatabaseManager.getInstance().initDatabase();

        setupWindow();
        setupMenuBar();
        setupToolBar();
        setupMainPanel();
        setupPanels();

        // Apply global settings on startup
        SettingsPanel.loadGlobalSettings();
        SettingsPanel.applyGlobalSettings();
    }
    
    private void setupWindow() {
        setTitle("WorkGenio - Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        
        // Add shutdown management to correctly close the database
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                DatabaseManager.getInstance().closeConnection();
            }
        });
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
        
        JMenuItem newOrderItem = new JMenuItem("New Order");
        newOrderItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newOrderItem.addActionListener(e -> createNewOrder());
        
        JMenuItem newInvoiceItem = new JMenuItem("New Invoice");
        newInvoiceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        newInvoiceItem.addActionListener(e -> createNewInvoice());
        
        fileMenu.add(newCustomerItem);
        fileMenu.add(newProductItem);
        fileMenu.add(newOrderItem);
        fileMenu.add(newInvoiceItem);
        fileMenu.addSeparator();
        
        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.addActionListener(e -> openSettings());
        fileMenu.add(settingsItem);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
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
        
        JMenuItem ordersItem = new JMenuItem("Orders");
        ordersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        ordersItem.addActionListener(e -> showPanel("ORDERS"));
        
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
        homeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        homeItem.addActionListener(e -> showPanel("HOME"));
        
        JCheckBoxMenuItem showToolbarItem = new JCheckBoxMenuItem("Show Toolbar", true);
        showToolbarItem.addActionListener(e -> toggleToolbar(showToolbarItem.isSelected()));
        
        viewMenu.add(homeItem);
        viewMenu.addSeparator();
        viewMenu.add(showToolbarItem);
        
        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        
        JMenuItem aboutItem = new JMenuItem("About WorkGenio");
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

        toolBar.addSeparator();

        // Management buttons
        customersButton = createToolBarButton("👥", "Customers", "Customer Management");
        customersButton.addActionListener(e -> showPanel("CUSTOMERS"));
        toolBar.add(customersButton);

        productsButton = createToolBarButton("📦", "Products", "Product Management");
        productsButton.addActionListener(e -> showPanel("PRODUCTS"));
        toolBar.add(productsButton);

        ordersButton = createToolBarButton("📝", "Orders", "Order Management");
        ordersButton.addActionListener(e -> showPanel("ORDERS"));
        toolBar.add(ordersButton);

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

        settingsButton = createToolBarButton("⚙️", "Settings", "Application Settings");
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
        button.setPreferredSize(new Dimension(80, 60));
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
            case "CUSTOMERS" -> customersButton;
            case "PRODUCTS" -> productsButton;
            case "ORDERS" -> ordersButton;
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
        
        // Create home panel (dashboard)
        JPanel homePanel = createHomePanel();
        mainPanel.add(homePanel, "HOME");
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private JPanel createHomePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        
        // Welcome panel
        JPanel welcomePanel = new JPanel();
        welcomePanel.setLayout(new GridBagLayout());
        JLabel welcomeLabel = new JLabel("Welcome to WorkGenio");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 32));
        welcomeLabel.setForeground(new Color(34, 139, 34));
        
        JLabel subtitleLabel = new JLabel("Professional Business Management System");
        subtitleLabel.setFont(new Font("Arial", Font.ITALIC, 18));
        subtitleLabel.setForeground(new Color(105, 105, 105));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(10, 0, 0, 0);
        welcomePanel.add(welcomeLabel, gbc);
        
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 0, 20, 0);
        welcomePanel.add(subtitleLabel, gbc);
        
        // Quick stats or info panel
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel versionLabel = new JLabel("WorkGenio v1.0 - Use the toolbar above or File menu to navigate between modules");
        versionLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        versionLabel.setForeground(new Color(70, 130, 180));
        infoPanel.add(versionLabel);
        
        panel.add(welcomePanel, BorderLayout.CENTER);
        panel.add(infoPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void setupPanels() {
        // Initialize panels lazily to improve startup time
        // Panels will be created when first accessed
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
            }
        });
    }
    
    private void createNewOrder() {
        showPanel("ORDERS");
        SwingUtilities.invokeLater(() -> {
            if (ordersPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(ordersPanel);
                OrderDialog dialog = new OrderDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
            }
        });
    }
    
    private void createNewInvoice() {
        showPanel("INVOICES");
        SwingUtilities.invokeLater(() -> {
            if (invoicesPanel != null) {
                Window parentWindow = SwingUtilities.getWindowAncestor(invoicesPanel);
                InvoiceDialog dialog = new InvoiceDialog((JFrame) parentWindow, null);
                dialog.setVisible(true);
            }
        });
    }
    
    private void performQuickBackup() {
        try {
            BackupManager.getInstance().performBackup();
            JOptionPane.showMessageDialog(this,
                "Backup completed successfully!",
                "Backup",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
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
        String aboutText = """
            WorkGenio v1.0
            Professional Business Management System
            
            Features:
            • Customer Management
            • Product Catalog
            • Order Processing
            • Invoice Generation
            • Supplier Management
            • Warehouse Control
            • Sales Reports & Analytics
            • Backup & Restore
            
            © 2025 WorkGenio
            """;
        
        JOptionPane.showMessageDialog(this,
            aboutText,
            "About WorkGenio",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showKeyboardShortcuts() {
        String shortcutsText = """
            Keyboard Shortcuts:
            
            File Menu:
            Ctrl+Shift+N    New Customer
            Ctrl+Shift+P    New Product
            Ctrl+Shift+O    New Order
            Ctrl+Shift+I    New Invoice
            Ctrl+Q          Exit
            
            Navigation:
            Ctrl+1          Customers
            Ctrl+2          Products
            Ctrl+3          Orders
            Ctrl+4          Invoices
            Ctrl+5          Suppliers
            Ctrl+6          Warehouse
            Ctrl+H          Dashboard
            
            Reports:
            Ctrl+Shift+R    Sales Report
            Ctrl+Shift+S    Advanced Statistics
            Ctrl+Shift+W    Warehouse Report
            
            Tools:
            Ctrl+B          Backup Management
            F1              This Help
            """;
        
        JTextArea textArea = new JTextArea(shortcutsText);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        JOptionPane.showMessageDialog(this,
            scrollPane,
            "Keyboard Shortcuts",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showPanel(String panelName) {
        try {
            // Create panel if it doesn't exist
            if (!isPanelCreated(panelName)) {
                createPanel(panelName);
            }

            // Switch to the panel
            cardLayout.show(mainPanel, panelName);
            currentPanel = panelName;

            // Update window title
            updateWindowTitle(panelName);

            // Update active button
            JButton button = getButtonForPanel(panelName);
            if (button != null) {
                setActiveButton(button);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error opening " + panelName + ": " + e.getMessage(),
                "WorkGenio - Error",
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
                    backupPanel = new BackupPanel();
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
        }
    }
    
    private void updateWindowTitle(String panelName) {
        String title = "WorkGenio - ";
        switch (panelName) {
            case "HOME": title += "Dashboard"; break;
            case "CUSTOMERS": title += "Customer Management"; break;
            case "PRODUCTS": title += "Product Management"; break;
            case "ORDERS": title += "Order Management"; break;
            case "INVOICES": title += "Invoice Management"; break;
            case "SUPPLIERS": title += "Supplier Management"; break;
            case "WAREHOUSE": title += "Warehouse Management"; break;
            case "SALES_REPORT": title += "Sales Report"; break;
            case "ADVANCED_STATS": title += "Advanced Statistics"; break;
            case "WAREHOUSE_REPORT": title += "Warehouse Report"; break;
            case "BACKUP": title += "Backup Management"; break;
            case "SETTINGS": title += "Settings"; break;
            default: title += "Management System";
        }
        setTitle(title);
    }
    
    private void openSettings() {
        showPanel("SETTINGS");
    }
    
    public static void main(String[] args) {
        // Add this code block for macOS integration
        final String os = System.getProperty("os.name");
        if (os != null && os.startsWith("Mac OS X")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "WorkGenio");
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            MainWindow mainWindow = new MainWindow();
            mainWindow.setVisible(true);
        });
    }
}