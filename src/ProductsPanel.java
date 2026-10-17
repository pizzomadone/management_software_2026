import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;

public class ProductsPanel extends JPanel {
    private static final String PRODUCT_COLUMN_WIDTHS_SETTING =
        "products.column_widths";
    private static final String PRODUCT_COLUMN_ORDER_SETTING =
        "products.column_order";
    private static final String[] PRODUCT_COLUMN_KEYS = {
        "id", "code", "name", "description", "sale_price_inc_vat", "physical",
        "reserved", "available", "on_order", "category", "unit",
        "min_qty", "active", "supplier", "position", "vat", "photo"
    };
    private static final int[] PRODUCT_COLUMN_MIN_WIDTHS = {
        30, 54, 60, 68, 44, 42, 46, 50, 46,
        54, 36, 48, 40, 54, 48, 44, 52
    };
    private static final int[] PRODUCT_COLUMN_INITIAL_CAPS = {
        38, 105, 145, 175, 64, 60, 66, 74, 68,
        120, 56, 68, 58, 140, 88, 58, 52
    };
    private static final String[] PRODUCT_COLUMN_TOOLTIPS = {
        "Internal product ID",
        "Product code or SKU",
        "Product name",
        "Product description",
        "Customer sale price incl. VAT",
        "Units physically in stock",
        "Units committed to customer orders",
        "Physical stock minus reserved units",
        "Units ordered from suppliers, not yet received",
        "Product category",
        "Unit of measure",
        "Minimum stock level before reorder",
        "Whether the product is active",
        "Preferred supplier",
        "Warehouse storage location",
        "VAT rate applied to the product",
        "Primary product photo"
    };

    private JTable productsTable;
    private JScrollPane productsScrollPane;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton detailsButton;
    private JButton editButton;
    private JButton lotsButton;
    private JButton deleteButton;
    private JButton refreshButton;
    // A catalog page stays small even with millions of photo BLOBs in SQLite.
    private static final int PAGE_SIZE = 100;
    private int currentPage;
    private int productCount;
    private String activeSearch = "";
    private JButton previousPageButton;
    private JButton nextPageButton;
    private JSpinner pageSpinner;
    private JLabel pageCountLabel;
    private boolean updatingPageSpinner;
    private final Map<String, ImageIcon> pageThumbnails = new HashMap<>();
    private final Set<String> pendingThumbnails = new LinkedHashSet<>();
    // Decode one visible thumbnail per tick instead of blocking the entire list load.
    private final Timer thumbnailTimer = new Timer(35, e -> loadNextThumbnail());
    private Timer photoPreviewTimer;
    private JWindow photoPreviewWindow;
    private boolean photoPreviewVisible;
    private final Set<Integer> missingPurchaseCostProductIds = new HashSet<>();
    
    public ProductsPanel() {
        setupPanel();
        initComponents();
        loadProducts();
    }
    
    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }
    
    private void initComponents() {
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Products"));
        
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchProducts());
        
        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(TableInteractionUtil.withClearButton(searchField, this::searchProducts));
        searchPanel.add(searchButton);
        
        // Products table
        String[] columns = {"ID", "Code", "Name", "Description", "Sale Price incl. VAT", "Physical", "Reserved", "Available", "On Order", "Category", "Unit", "Min Qty", "Active", "Supplier", "Position", "VAT %", "Photo"};
        for (int i = 0; i < columns.length; i++) columns[i] = columns[i].trim();
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 16 ? String.class : Object.class;
            }
        };
        productsTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                int modelRow = convertRowIndexToModel(row);
                Object idValue = tableModel.getValueAt(modelRow, 0);
                boolean missingCost = idValue instanceof Number
                    && missingPurchaseCostProductIds.contains(((Number)idValue).intValue());
                if (!isCellSelected(row, column) && missingCost) {
                    component.setBackground(new Color(255, 226, 170));
                }
                if (component instanceof JComponent jc && missingCost) {
                    String current = jc.getToolTipText();
                    String warning = I18n.tr(
                        "Purchase cost is missing. Purchases and sales are blocked until the product is corrected.");
                    jc.setToolTipText(current == null || current.isBlank()
                        ? warning : current + " — " + warning);
                }
                return component;
            }
        };
        productsTable.setRowHeight(34);
        productsTable.setRowMargin(0);
        if (!restoreProductColumnOrder()) {
            int defaultPhotoView = productsTable.convertColumnIndexToView(16);
            if (defaultPhotoView >= 0) productsTable.moveColumn(defaultPhotoView, 0);
        }
        int photoViewColumn = productsTable.convertColumnIndexToView(16);
        TableColumn photoColumn = productsTable.getColumnModel().getColumn(photoViewColumn);
        photoColumn.setMinWidth(52);
        photoColumn.setPreferredWidth(52);
        photoColumn.setMaxWidth(Integer.MAX_VALUE);
        photoColumn.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, "", selected, focus, row, column);
                String filename = value instanceof String ? (String)value : null;
                if (filename != null && !filename.isBlank() &&
                        !pageThumbnails.containsKey(filename)) {
                    pendingThumbnails.add(filename);
                    if (!thumbnailTimer.isRunning()) thumbnailTimer.start();
                }
                label.setIcon(filename == null ? null : pageThumbnails.get(filename));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setVerticalAlignment(SwingConstants.CENTER);
                label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
                label.setToolTipText(filename != null && !filename.isBlank()
                    ? I18n.tr("Hold to preview") : null);
                return label;
            }
        });
        configureProductTablePresentation();
        TableStyleUtil.applyProductEnhancements(productsTable);
        productsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productsTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Enable column sorting
        TableSorterUtil.enableSorting(productsTable);

        // Add Delete key shortcut
        TableInteractionUtil.addDeleteKeyAction(productsTable, this::deleteSelectedProduct);

        // Add context menu
        TableInteractionUtil.addContextMenu(productsTable,
            new TableInteractionUtil.TableAction("Add Product", () -> showProductDialog(null), false),
            new TableInteractionUtil.TableAction("View Details", this::viewSelectedProduct),
            new TableInteractionUtil.TableAction("Edit", this::editSelectedProduct),
            new TableInteractionUtil.TableAction("Lots", this::showSelectedProductLots),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelectedProduct),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadProducts, false)
        );

        // Photo thumbnail: press-and-hold shows a temporary preview.
        // A normal click on the photo does not open another window.
        productsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                startPhotoPreview(evt);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                endPhotoPreview();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                endPhotoPreview();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    viewSelectedProduct();
                }
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("Add");
        detailsButton = new JButton("View Details");
        editButton = new JButton("Edit");
        lotsButton = new JButton("Lots");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        searchButton.setToolTipText(I18n.tr("Search products by code, name or description."));
        addButton.setToolTipText(I18n.tr("Create a new product."));
        detailsButton.setToolTipText(I18n.tr("Open the selected product details."));
        editButton.setToolTipText(I18n.tr("Edit the selected product."));
        lotsButton.setToolTipText(I18n.tr("View FIFO lots for the selected product."));
        deleteButton.setToolTipText(I18n.tr("Delete the selected product."));
        refreshButton.setToolTipText(I18n.tr("Reload the product list."));
        
        addButton.addActionListener(e -> showProductDialog(null));
        detailsButton.addActionListener(e -> viewSelectedProduct());
        editButton.addActionListener(e -> editSelectedProduct());
        lotsButton.addActionListener(e -> showSelectedProductLots());
        deleteButton.addActionListener(e -> deleteSelectedProduct());
        refreshButton.addActionListener(e -> loadProducts());
        
        buttonPanel.add(addButton);
        buttonPanel.add(detailsButton);
        buttonPanel.add(editButton);
        buttonPanel.add(lotsButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        // SQL-backed pages: navigation never materializes the complete catalog.
        previousPageButton = new JButton("◀");
        nextPageButton = new JButton("▶");
        pageSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
        ((JSpinner.DefaultEditor) pageSpinner.getEditor()).getTextField().setColumns(4);
        pageCountLabel = new JLabel("/ 1 (0)");
        previousPageButton.setToolTipText(I18n.tr("Previous page"));
        nextPageButton.setToolTipText(I18n.tr("Next page"));
        previousPageButton.addActionListener(e -> { if (currentPage > 0) { currentPage--; loadProducts(); } });
        nextPageButton.addActionListener(e -> {
            if (currentPage < (productCount - 1) / PAGE_SIZE) { currentPage++; loadProducts(); }
        });
        pageSpinner.addChangeListener(e -> {
            if (!updatingPageSpinner) {
                currentPage = ((Number) pageSpinner.getValue()).intValue() - 1;
                loadProducts();
            }
        });
        buttonPanel.add(previousPageButton);
        buttonPanel.add(pageSpinner);
        buttonPanel.add(pageCountLabel);
        buttonPanel.add(nextPageButton);
        
        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        productsScrollPane = new JScrollPane(productsTable);
        productsScrollPane.setHorizontalScrollBarPolicy(
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        productsScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                scheduleProductColumnFit();
            }
        });
        add(productsScrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        fitProductColumns();
        updateButtonStates();
    }
    
    private void configureProductTablePresentation() {
        // Keep the table width fixed while redistributing a manual resize
        // across all remaining columns, as in the original product-table behavior.
        productsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        TableCellRenderer headerDelegate = productsTable.getTableHeader().getDefaultRenderer();
        productsTable.getTableHeader().setDefaultRenderer((table, value, selected, focus, row, column) -> {
            Component component = headerDelegate.getTableCellRendererComponent(
                table, value, selected, focus, row, column);
            if (component instanceof JLabel label) {
                String text = value == null ? "" : value.toString().trim();
                label.setText(text);
                label.setHorizontalAlignment(SwingConstants.CENTER);

                Font base = table.getTableHeader().getFont();
                float size = base.getSize2D();
                float minSize = Math.max(9.5f, size - 2.5f);
                int available = Math.max(12,
                    table.getColumnModel().getColumn(column).getWidth() - 6);
                Font candidate = base;
                while (size > minSize &&
                        label.getFontMetrics(candidate).stringWidth(text) > available) {
                    size -= 0.5f;
                    candidate = base.deriveFont(size);
                }
                label.setFont(candidate);
            }
            return component;
        });

        productsTable.getTableHeader().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewColumn = productsTable.getTableHeader().columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    productsTable.getTableHeader().setToolTipText(null);
                    return;
                }
                int modelColumn = productsTable.convertColumnIndexToModel(viewColumn);
                String tooltip = modelColumn >= 0 && modelColumn < PRODUCT_COLUMN_TOOLTIPS.length
                    ? I18n.tr(PRODUCT_COLUMN_TOOLTIPS[modelColumn])
                    : null;
                productsTable.getTableHeader().setToolTipText(tooltip);
            }
        });
        productsTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                productsTable.getTableHeader().setToolTipText(null);
            }
        });

        TableCellRenderer centeredRenderer =
            new AdaptiveProductCellRenderer(SwingConstants.CENTER);
        TableCellRenderer textRenderer =
            new AdaptiveProductCellRenderer(SwingConstants.LEFT);

        int[] numericModelColumns = {0, 4, 5, 6, 7, 8, 11, 12, 15};
        for (int modelColumn : numericModelColumns) {
            int viewColumn = productsTable.convertColumnIndexToView(modelColumn);
            if (viewColumn >= 0) {
                productsTable.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(centeredRenderer);
            }
        }

        int[] textModelColumns = {1, 2, 3, 9, 10, 13, 14};
        for (int modelColumn : textModelColumns) {
            int viewColumn = productsTable.convertColumnIndexToView(modelColumn);
            if (viewColumn >= 0) {
                productsTable.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(textRenderer);
            }
        }

        productsTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> {
                        saveProductColumnWidths();
                        saveProductColumnOrder();
                    });
                }
            }
        });
    }

    private static final class AdaptiveProductCellRenderer
            extends DefaultTableCellRenderer {
        private final int alignment;

        private AdaptiveProductCellRenderer(int alignment) {
            this.alignment = alignment;
            setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            String fullText;
            if (value == null) {
                fullText = "";
            } else if (value instanceof String stringValue) {
                fullText = I18n.tr(stringValue.trim());
            } else {
                fullText = String.valueOf(value).trim();
            }

            JLabel label = (JLabel)super.getTableCellRendererComponent(
                table, fullText, selected, focus, row, column);
            label.setHorizontalAlignment(alignment);
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

            Font base = table.getFont();
            float size = base.getSize2D();
            float minSize = Math.max(10.5f, size - 2.0f);
            int available = Math.max(8,
                table.getColumnModel().getColumn(column).getWidth() - 6);

            Font candidate = base;
            FontMetrics metrics = label.getFontMetrics(candidate);
            while (size > minSize &&
                    metrics.stringWidth(fullText) > available) {
                size = Math.max(minSize, size - 0.5f);
                candidate = base.deriveFont(size);
                metrics = label.getFontMetrics(candidate);
            }

            label.setFont(candidate);
            String displayText = ellipsize(fullText, metrics, available);
            label.setText(displayText);
            label.setToolTipText(displayText.equals(fullText) ? null : fullText);
            return label;
        }

        private static String ellipsize(String text, FontMetrics metrics,
                int availableWidth) {
            if (text == null || text.isEmpty() ||
                    metrics.stringWidth(text) <= availableWidth) {
                return text == null ? "" : text;
            }

            String suffix = "...";
            int suffixWidth = metrics.stringWidth(suffix);
            if (suffixWidth >= availableWidth) return suffix;

            int low = 0;
            int high = text.length();
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                String candidate = text.substring(0, mid).stripTrailing() + suffix;
                if (metrics.stringWidth(candidate) <= availableWidth) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            return text.substring(0, low).stripTrailing() + suffix;
        }
    }

    private void fitProductColumns() {
        int[] requested = loadSavedProductColumnWidths();
        if (requested == null) requested = calculateDefaultProductColumnWidths();

        int viewportWidth = productsScrollPane == null
            ? 0 : productsScrollPane.getViewport().getExtentSize().width;
        int[] fitted = fitWidthsToViewport(requested, viewportWidth);

        for (int modelColumn = 0; modelColumn < tableModel.getColumnCount(); modelColumn++) {
            int viewColumn = productsTable.convertColumnIndexToView(modelColumn);
            if (viewColumn < 0) continue;

            TableColumn column = productsTable.getColumnModel().getColumn(viewColumn);
            column.setResizable(true);
            column.setMinWidth(PRODUCT_COLUMN_MIN_WIDTHS[modelColumn]);
            column.setMaxWidth(Integer.MAX_VALUE);
            column.setPreferredWidth(fitted[modelColumn]);
            column.setWidth(fitted[modelColumn]);
        }

        productsTable.doLayout();
    }

    private int[] calculateDefaultProductColumnWidths() {
        int[] widths = PRODUCT_COLUMN_MIN_WIDTHS.clone();
        FontMetrics cellMetrics = productsTable.getFontMetrics(productsTable.getFont());
        FontMetrics headerMetrics = productsTable.getTableHeader()
            .getFontMetrics(productsTable.getTableHeader().getFont());

        for (int modelColumn = 0; modelColumn < tableModel.getColumnCount(); modelColumn++) {
            if (modelColumn == 16) {
                widths[modelColumn] = 52;
                continue;
            }

            String header = I18n.tr(tableModel.getColumnName(modelColumn)).trim();
            int width = headerMetrics.stringWidth(header) + 8;

            for (int row = 0; row < tableModel.getRowCount(); row++) {
                Object value = tableModel.getValueAt(row, modelColumn);
                if (value == null) continue;
                String text = value instanceof String
                    ? I18n.tr(((String)value).trim())
                    : String.valueOf(value).trim();
                width = Math.max(width, cellMetrics.stringWidth(text) + 8);
            }

            widths[modelColumn] = Math.max(PRODUCT_COLUMN_MIN_WIDTHS[modelColumn],
                Math.min(PRODUCT_COLUMN_INITIAL_CAPS[modelColumn], width));
        }
        return widths;
    }

    private int[] loadSavedProductColumnWidths() {
        String raw = SettingsPanel.getGlobalSetting(
            PRODUCT_COLUMN_WIDTHS_SETTING, "").trim();
        if (raw.isEmpty()) return null;

        int[] widths = new int[PRODUCT_COLUMN_KEYS.length];
        boolean[] found = new boolean[PRODUCT_COLUMN_KEYS.length];

        try {
            for (String entry : raw.split(";")) {
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator >= entry.length() - 1) continue;
                String key = entry.substring(0, separator).trim();
                int value = Integer.parseInt(entry.substring(separator + 1).trim());

                for (int i = 0; i < PRODUCT_COLUMN_KEYS.length; i++) {
                    if (PRODUCT_COLUMN_KEYS[i].equals(key)) {
                        widths[i] = Math.max(PRODUCT_COLUMN_MIN_WIDTHS[i], value);
                        found[i] = true;
                        break;
                    }
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }

        for (boolean present : found) if (!present) return null;
        return widths;
    }

    private void saveProductColumnWidths() {
        if (productsTable == null || tableModel == null) return;

        StringBuilder value = new StringBuilder();
        for (int modelColumn = 0; modelColumn < PRODUCT_COLUMN_KEYS.length; modelColumn++) {
            int viewColumn = productsTable.convertColumnIndexToView(modelColumn);
            if (viewColumn < 0) continue;
            if (value.length() > 0) value.append(';');
            value.append(PRODUCT_COLUMN_KEYS[modelColumn]).append('=')
                .append(productsTable.getColumnModel().getColumn(viewColumn).getWidth());
        }

        SettingsPanel.setGlobalSetting(PRODUCT_COLUMN_WIDTHS_SETTING,
            value.toString());
    }

    private boolean restoreProductColumnOrder() {
        String raw = SettingsPanel.getGlobalSetting(
            PRODUCT_COLUMN_ORDER_SETTING, "").trim();
        if (raw.isEmpty()) return false;

        String[] keys = raw.split(",");
        if (keys.length != PRODUCT_COLUMN_KEYS.length) return false;

        boolean[] seen = new boolean[PRODUCT_COLUMN_KEYS.length];
        int[] modelOrder = new int[PRODUCT_COLUMN_KEYS.length];
        for (int view = 0; view < keys.length; view++) {
            int model = productModelColumnForKey(keys[view].trim());
            if (model < 0 || seen[model]) return false;
            seen[model] = true;
            modelOrder[view] = model;
        }

        for (int targetView = 0; targetView < modelOrder.length; targetView++) {
            int currentView = productsTable.convertColumnIndexToView(modelOrder[targetView]);
            if (currentView < 0) return false;
            if (currentView != targetView)
                productsTable.moveColumn(currentView, targetView);
        }
        return true;
    }

    private void saveProductColumnOrder() {
        if (productsTable == null || tableModel == null) return;

        StringBuilder value = new StringBuilder();
        for (int view = 0; view < productsTable.getColumnCount(); view++) {
            int model = productsTable.convertColumnIndexToModel(view);
            if (model < 0 || model >= PRODUCT_COLUMN_KEYS.length) continue;
            if (value.length() > 0) value.append(',');
            value.append(PRODUCT_COLUMN_KEYS[model]);
        }
        if (value.length() > 0)
            SettingsPanel.setGlobalSetting(PRODUCT_COLUMN_ORDER_SETTING,
                value.toString());
    }

    private int productModelColumnForKey(String key) {
        for (int i = 0; i < PRODUCT_COLUMN_KEYS.length; i++) {
            if (PRODUCT_COLUMN_KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    private int[] fitWidthsToViewport(int[] requested, int viewportWidth) {
        int[] result = requested.clone();
        if (viewportWidth <= 0) return result;

        int requestedTotal = 0;
        for (int width : requested) requestedTotal += width;
        if (requestedTotal <= 0) return result;

        double scale = viewportWidth / (double)requestedTotal;
        int total = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.max(PRODUCT_COLUMN_MIN_WIDTHS[i],
                (int)Math.round(requested[i] * scale));
            total += result[i];
        }

        // If minimum widths pushed the total beyond the viewport, shrink only
        // columns that still have room above their minimum.
        while (total > viewportWidth) {
            boolean changed = false;
            for (int i = 0; i < result.length && total > viewportWidth; i++) {
                if (result[i] > PRODUCT_COLUMN_MIN_WIDTHS[i]) {
                    result[i]--;
                    total--;
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // Give any remaining pixels to useful text columns first instead of
        // inflating ID, VAT or other naturally small columns.
        int[] growOrder = {3, 2, 13, 9, 1, 14, 8, 7, 6, 5, 4, 12, 10, 11, 15, 0, 16};
        int growIndex = 0;
        while (total < viewportWidth && growOrder.length > 0) {
            result[growOrder[growIndex % growOrder.length]]++;
            total++;
            growIndex++;
        }

        return result;
    }

    private void scheduleProductColumnFit() {
        SwingUtilities.invokeLater(this::fitProductColumns);
    }

    private static String trimCellText(String value) {
        return value == null ? "" : value.trim();
    }

    private void startPhotoPreview(MouseEvent evt) {
        cancelPhotoPreviewTimer();
        if (!SwingUtilities.isLeftMouseButton(evt)) return;

        int viewRow = productsTable.rowAtPoint(evt.getPoint());
        int viewColumn = productsTable.columnAtPoint(evt.getPoint());
        if (viewRow < 0 || viewColumn < 0 ||
                productsTable.convertColumnIndexToModel(viewColumn) != 16) return;

        int modelRow = productsTable.convertRowIndexToModel(viewRow);
        if (!(tableModel.getValueAt(modelRow, 16) instanceof String filename) || filename.isBlank()) return;

        Point screenPoint = evt.getLocationOnScreen();
        photoPreviewTimer = new Timer(225, e -> showPhotoPreview(modelRow, screenPoint));
        photoPreviewTimer.setRepeats(false);
        photoPreviewTimer.start();
    }

    private void showPhotoPreview(int modelRow, Point screenPoint) {
        try {
            int productId = (int)tableModel.getValueAt(modelRow, 0);
            String filename = ProductImageStore.getInstance().primaryFilename(productId);
            if (filename == null || filename.isBlank()) return;

            ImageIcon preview = ProductImageStore.getInstance().icon(filename, 480);
            if (preview == null) return;

            closePhotoPreviewWindow();
            Window owner = SwingUtilities.getWindowAncestor(this);
            photoPreviewWindow = owner == null ? new JWindow() : new JWindow(owner);
            photoPreviewWindow.setFocusableWindowState(false);

            JLabel imageLabel = new JLabel(preview);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            photoPreviewWindow.add(imageLabel);
            photoPreviewWindow.pack();

            GraphicsConfiguration gc = productsTable.getGraphicsConfiguration();
            Rectangle bounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int minX = bounds.x + insets.left;
            int minY = bounds.y + insets.top;
            int maxX = bounds.x + bounds.width - insets.right - photoPreviewWindow.getWidth();
            int maxY = bounds.y + bounds.height - insets.bottom - photoPreviewWindow.getHeight();
            int x = Math.max(minX, Math.min(screenPoint.x + 14, maxX));
            int y = Math.max(minY, Math.min(screenPoint.y + 14, maxY));

            photoPreviewWindow.setLocation(x, y);
            photoPreviewWindow.setVisible(true);
            photoPreviewVisible = true;
        } catch (SQLException ignored) {
            closePhotoPreviewWindow();
        }
    }

    private void endPhotoPreview() {
        cancelPhotoPreviewTimer();
        closePhotoPreviewWindow();
    }

    private void cancelPhotoPreviewTimer() {
        if (photoPreviewTimer != null) {
            photoPreviewTimer.stop();
            photoPreviewTimer = null;
        }
    }

    private void closePhotoPreviewWindow() {
        if (photoPreviewWindow != null) {
            photoPreviewWindow.setVisible(false);
            photoPreviewWindow.dispose();
            photoPreviewWindow = null;
        }
        photoPreviewVisible = false;
    }

    private void updateButtonStates() {
        boolean isRowSelected = productsTable.getSelectedRow() != -1;
        detailsButton.setEnabled(isRowSelected);
        editButton.setEnabled(isRowSelected);
        lotsButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
    }
    
    public void loadProducts() {
        loadProductPage();
    }

    private void searchProducts() {
        activeSearch = searchField.getText().trim();
        currentPage = 0;
        loadProductPage();
    }

    private void loadNextThumbnail() {
        if (pendingThumbnails.isEmpty()) { thumbnailTimer.stop(); return; }
        String filename = pendingThumbnails.iterator().next();
        pendingThumbnails.remove(filename);
        // The SQLite owner connection is shared: keep access on the UI thread,
        // but decode only one thumbnail per timer event and only after painting.
        pageThumbnails.put(filename, ProductImageStore.getInstance().iconCover(filename, 48, 30));
        if (productsTable != null) productsTable.repaint();
        if (pendingThumbnails.isEmpty()) thumbnailTimer.stop();
    }

    private void loadProductPage() {
        // This path intentionally never loads photo image_data in the catalog query.
        String filter = activeSearch == null ? "" : activeSearch.trim();
        boolean filtered = !filter.isEmpty();
        String where = filtered
            ? " WHERE p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ? "
            : "";
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            int total;
            try (PreparedStatement count = conn.prepareStatement(
                    "SELECT COUNT(*) FROM products p" + where)) {
                if (filtered) bindSearch(count, filter);
                try (ResultSet rs = count.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Unable to count products");
                    total = rs.getInt(1);
                }
            }
            int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            currentPage = Math.max(0, Math.min(currentPage, pages - 1));
            String query = """
                SELECT p.*, COALESCE(sm.minimum_quantity, 0) AS minimum_quantity,
                       f.company_name AS supplier_name,
                       ps.purchase_price AS preferred_supplier_cost,
                       (SELECT filename FROM product_images pi WHERE pi.product_id=p.id
                        ORDER BY pi.is_primary DESC, pi.sort_order, pi.id LIMIT 1) AS primary_image,
                       COALESCE(SUM(CASE
                           WHEN so.status IN ('Draft','Confirmed','In Transit') THEN sod.quantity
                           ELSE 0 END), 0) AS on_order_quantity
                FROM (
                    SELECT * FROM products p
                """ + where + """
                    ORDER BY p.name COLLATE NOCASE, p.id LIMIT ? OFFSET ?
                ) p
                LEFT JOIN minimum_stock sm ON sm.product_id=p.id
                LEFT JOIN product_suppliers ps ON ps.product_id=p.id AND ps.is_preferred=1
                LEFT JOIN suppliers f ON ps.supplier_id=f.id
                LEFT JOIN supplier_order_details sod ON sod.product_id=p.id
                LEFT JOIN supplier_orders so ON so.id=sod.order_id
                GROUP BY p.id
                ORDER BY p.name COLLATE NOCASE, p.id
                """;
            List<Vector<Object>> rows = new ArrayList<>();
            Set<Integer> missingCosts = new HashSet<>();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                int index = filtered ? bindSearch(stmt, filter) : 1;
                stmt.setInt(index++, PAGE_SIZE);
                stmt.setInt(index, currentPage * PAGE_SIZE);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        int id = rs.getInt("id");
                        int physical = rs.getInt("quantity");
                        int reserved = rs.getInt("reserved_quantity");
                        row.add(id);
                        row.add(trimCellText(rs.getString("code")));
                        row.add(trimCellText(rs.getString("name")));
                        row.add(trimCellText(rs.getString("description")));
                        row.add(rs.getDouble("sale_price_inc_vat"));
                        row.add(physical);
                        row.add(reserved);
                        row.add(physical - reserved);
                        row.add(rs.getInt("on_order_quantity"));
                        row.add(trimCellText(rs.getString("category")));
                        row.add(trimCellText(rs.getString("unit_of_measure")));
                        row.add(rs.getInt("minimum_quantity"));
                        row.add(rs.getInt("active") == 1 ? "Yes" : "No");
                        row.add(trimCellText(rs.getString("supplier_name")));
                        row.add(trimCellText(rs.getString("warehouse_position")));
                        row.add(rs.getDouble("vat_rate"));
                        // A filename is cheap to fetch; JPEG BLOB reads are lazy.
                        row.add(rs.getString("primary_image"));
                        if ((rs.getInt("active") == 1 || physical > 0)
                                && (rs.getObject("preferred_supplier_cost") == null ||
                                    Numbers.nonNegative(rs.getObject("preferred_supplier_cost")) <= 0))
                            missingCosts.add(id);
                        rows.add(row);
                    }
                }
            }
            thumbnailTimer.stop();
            pendingThumbnails.clear();
            pageThumbnails.clear();
            tableModel.setRowCount(0);
            for (Vector<Object> row : rows) tableModel.addRow(row);
            missingPurchaseCostProductIds.clear();
            missingPurchaseCostProductIds.addAll(missingCosts);
            productCount = total;
            updatingPageSpinner = true;
            try {
                pageSpinner.setModel(new SpinnerNumberModel(currentPage + 1, 1, pages, 1));
            } finally {
                updatingPageSpinner = false;
            }
            pageCountLabel.setText("/ " + pages + " (" + total + ")");
            previousPageButton.setEnabled(currentPage > 0);
            nextPageButton.setEnabled(currentPage + 1 < pages);
            if (productsScrollPane != null)
                productsScrollPane.getVerticalScrollBar().setValue(0);
            scheduleProductColumnFit();
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this,
                "Error loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int bindSearch(PreparedStatement stmt, String term) throws SQLException {
        String pattern = "%" + term + "%";
        for (int i = 1; i <= 3; i++) stmt.setString(i, pattern);
        return 4;
    }

    private void showProductDialog(Product product) {
        // Get the parent window for the dialog
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        
        ProductDialog dialog;
        if (parentWindow instanceof JFrame) {
            dialog = new ProductDialog((JFrame) parentWindow, product);
        } else {
            dialog = new ProductDialog((JDialog) parentWindow, product);
        }
        
        dialog.setVisible(true);
        if (dialog.isProductSaved()) {
            Integer productIdToReselect = product != null ? product.getId() : null;
            loadProducts();
            if (productIdToReselect != null) {
                selectProductById(productIdToReselect);
            }
        }
    }

    private void selectProductById(int productId) {
        if (selectVisibleProductById(productId)) return;
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String name;
            try (PreparedStatement p = conn.prepareStatement("SELECT name FROM products WHERE id=?")) {
                p.setInt(1, productId);
                try (ResultSet rs = p.executeQuery()) {
                    if (!rs.next()) return;
                    name = rs.getString(1);
                }
            }
            // When a product is outside the current page, locate it by its
            // stable name/id ordering rather than loading every product.
            int before;
            try (PreparedStatement p = conn.prepareStatement("""
                    SELECT COUNT(*) FROM products
                    WHERE name COLLATE NOCASE < ? OR (name COLLATE NOCASE = ? AND id < ?)
                    """)) {
                p.setString(1, name);
                p.setString(2, name);
                p.setInt(3, productId);
                try (ResultSet rs = p.executeQuery()) { rs.next(); before = rs.getInt(1); }
            }
            activeSearch = "";
            searchField.setText("");
            currentPage = before / PAGE_SIZE;
            loadProductPage();
            selectVisibleProductById(productId);
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this, "Error locating product: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean selectVisibleProductById(int productId) {
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            Object value = tableModel.getValueAt(modelRow, 0);
            if (value instanceof Number number && number.intValue() == productId) {
                int viewRow = productsTable.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    productsTable.setRowSelectionInterval(viewRow, viewRow);
                    productsTable.scrollRectToVisible(productsTable.getCellRect(viewRow, 0, true));
                }
                return true;
            }
        }
        return false;
    }
    
    private void viewSelectedProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = productsTable.convertRowIndexToModel(selectedRow);
        int productId = (int)tableModel.getValueAt(modelRow, 0);
        try {
            Product product = loadProduct(productId);
            if (product == null) return;
            Window owner = SwingUtilities.getWindowAncestor(this);
            ProductDetailsDialog details = new ProductDetailsDialog(owner, product);
            details.setVisible(true);
            if (details.isEditRequested()) showProductDialog(product);
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this, "Error loading product: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Product loadProduct(int productId) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT p.*, COALESCE(sm.minimum_quantity, 0) AS minimum_quantity,
                   ps.supplier_id AS preferred_supplier_id, f.company_name as supplier_name
            FROM products p
            LEFT JOIN minimum_stock sm ON sm.product_id = p.id
            LEFT JOIN product_suppliers ps ON ps.product_id=p.id AND ps.is_preferred=1
                LEFT JOIN suppliers f ON ps.supplier_id = f.id
            WHERE p.id = ?
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return null;
                Integer supplierId = getIntegerFromResultSet(rs, "preferred_supplier_id");
                return new Product(
                    rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("description"), rs.getDouble("sale_price_inc_vat"),
                    getIntegerFromResultSet(rs, "quantity", 0),
                    rs.getString("category") != null ? rs.getString("category") : "",
                    rs.getString("alternative_sku") != null ? rs.getString("alternative_sku") : "",
                    getDoubleFromResultSet(rs, "weight", 0.0),
                    rs.getString("unit_of_measure") != null ? rs.getString("unit_of_measure") : "pcs",
                    getIntegerFromResultSet(rs, "minimum_quantity", 0),
                    getDoubleFromResultSet(rs, "acquisition_cost", 0.0),
                    getIntegerFromResultSet(rs, "active", 1) == 1,
                    supplierId,
                    rs.getString("supplier_name") != null ? rs.getString("supplier_name") : "",
                    rs.getString("warehouse_position") != null ? rs.getString("warehouse_position") : "",
                    getDoubleFromResultSet(rs, "vat_rate", 0.0)
                );
            }
        }
    }

    private void showSelectedProductLots() {
        int selectedRow=productsTable.getSelectedRow();
        if(selectedRow<0)return;
        int row=productsTable.convertRowIndexToModel(selectedRow);
        int productId=((Number)tableModel.getValueAt(row,0)).intValue();
        String productName=String.valueOf(tableModel.getValueAt(row,2));
        Window owner=SwingUtilities.getWindowAncestor(this);
        new InventoryLotsDialog(owner,productId,productName).setVisible(true);
    }

    private void editSelectedProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = productsTable.convertRowIndexToModel(selectedRow);
            int productId = (int)tableModel.getValueAt(modelRow, 0);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = """
                    SELECT p.*, COALESCE(sm.minimum_quantity, 0) AS minimum_quantity,
                   ps.supplier_id AS preferred_supplier_id, f.company_name as supplier_name
                    FROM products p
                    LEFT JOIN minimum_stock sm ON sm.product_id = p.id
                    LEFT JOIN product_suppliers ps ON ps.product_id=p.id AND ps.is_preferred=1
                LEFT JOIN suppliers f ON ps.supplier_id = f.id
                    WHERE p.id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, productId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            // Safely read integer and double fields with proper type handling
                            Integer supplierId = getIntegerFromResultSet(rs, "preferred_supplier_id");
                            String supplierName = rs.getString("supplier_name");

                            int quantity = getIntegerFromResultSet(rs, "quantity", 0);
                            int minimumQuantity = getIntegerFromResultSet(rs, "minimum_quantity", 0);
                            int active = getIntegerFromResultSet(rs, "active", 1);
                            double acquisitionCost = getDoubleFromResultSet(rs, "acquisition_cost", 0.0);
                            double weight = getDoubleFromResultSet(rs, "weight", 0.0);
                            double vatRate = getDoubleFromResultSet(rs, "vat_rate", 0.0);

                            Product product = new Product(
                                rs.getInt("id"),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getDouble("sale_price_inc_vat"),
                                quantity,
                                rs.getString("category") != null ? rs.getString("category") : "",
                                rs.getString("alternative_sku") != null ? rs.getString("alternative_sku") : "",
                                weight,
                                rs.getString("unit_of_measure") != null ? rs.getString("unit_of_measure") : "pcs",
                                minimumQuantity,
                                acquisitionCost,
                                active == 1,
                                supplierId,
                                supplierName != null ? supplierName : "",
                                rs.getString("warehouse_position") != null ? rs.getString("warehouse_position") : "",
                                vatRate
                            );
                            showProductDialog(product);
                        }
                    }
                }
            } catch (SQLException e) {
                Dialogs.showMessageDialog(this,
                    "Error loading product: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void deleteSelectedProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = productsTable.convertRowIndexToModel(selectedRow);
            int id = (int)tableModel.getValueAt(modelRow, 0);
            String name = (String)tableModel.getValueAt(modelRow, 2);
            
            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                
                // Check for existing dependencies
                boolean hasOrders = hasProductInOrders(conn, id);
                boolean hasInvoices = hasProductInInvoices(conn, id);
                boolean hasSupplierOrders = hasProductInSupplierOrders(conn, id);
                boolean hasSupplierLinks = hasProductInSupplierLinks(conn, id);
                boolean hasWarehouseMovements = hasProductInWarehouseMovements(conn, id);
                boolean hasStock = hasNonZeroProductStock(conn, id);
                boolean hasFifoLots = hasProductInInventoryLots(conn, id);
                boolean hasMinStock = hasProductInMinStock(conn, id);
                boolean hasReservations = hasProductInReservations(conn, id);
                boolean hasNotifications = hasProductInNotifications(conn, id);
                
                boolean hasCriticalData = hasOrders || hasInvoices || hasSupplierOrders ||
                    hasWarehouseMovements || hasReservations || hasStock || hasFifoLots;
                if (hasCriticalData) {
                    StringBuilder message = new StringBuilder();
                    message.append("Product '").append(name).append("' cannot be deleted because it is part of critical business history:\n");
                    
                    if (hasOrders) message.append("- Customer orders\n");
                    if (hasInvoices) message.append("- Invoice entries\n");
                    if (hasSupplierOrders) message.append("- Supplier orders\n");
                    if (hasSupplierLinks) message.append("- Supplier associations\n");
                    if (hasWarehouseMovements) message.append("- Warehouse movements\n");
                    if (hasReservations) message.append("- Active or historical stock reservations\n");
                    if (hasStock) message.append("- Non-zero stock or reserved units\n");
                    if (hasFifoLots) message.append("- FIFO inventory lots, including historical lots\n");
                    message.append("\nDeleting it would erase stock or business history. " +
                        "Set the product as inactive instead.");
                    Dialogs.showMessageDialog(this,
                        message.toString(),
                        "Product Cannot Be Deleted", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (hasSupplierLinks || hasMinStock || hasNotifications) {
                    String details = (hasSupplierLinks ? "- Supplier associations\n" : "") +
                        (hasMinStock ? "- Minimum stock settings\n" : "") +
                        (hasNotifications ? "- Warehouse notifications\n" : "");
                    int cleanup = Dialogs.showConfirmDialog(this,
                        "Product '" + name + "' has only non-critical configuration data:\n" + details +
                        "\nThese records will also be deleted. Continue?",
                        "Confirm Product Cleanup", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (cleanup == JOptionPane.YES_OPTION) {
                        deleteProductAndNonCriticalData(conn, id);
                        loadProducts();
                    }
                    return;
                }

                // Safe to delete - no foreign key references
                int result = Dialogs.showConfirmDialog(this,
                    "Are you sure you want to delete the product '" + name + "'?",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                    
                if (result == JOptionPane.YES_OPTION) {
                    java.util.List<String> imageFiles = ProductImageStore.getInstance().filenamesForProduct(conn, id);
                    deleteProductIfEmpty(conn, id);
                    ProductImageStore.getInstance().deleteNamedFiles(imageFiles);
                    loadProducts();

                    Dialogs.showMessageDialog(this,
                        "Product deleted successfully",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error deleting the product: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteProductAndNonCriticalData(Connection conn, int productId) throws SQLException {
        java.util.List<String> imageFiles = ProductImageStore.getInstance().filenamesForProduct(conn, productId);
        DatabaseManager.beginTransaction(conn);
        try {
            String[] statements = {
                "DELETE FROM product_suppliers WHERE product_id = ?",
                "DELETE FROM warehouse_notifications WHERE product_id = ?",
                "DELETE FROM minimum_stock WHERE product_id = ?"
            };
            for (String statement : statements) {
                try (PreparedStatement pstmt = conn.prepareStatement(statement)) {
                    pstmt.setInt(1, productId);
                    pstmt.executeUpdate();
                }
            }
            deleteProductIfEmpty(conn, productId);
            conn.commit();
            ProductImageStore.getInstance().deleteNamedFiles(imageFiles);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private boolean hasNonZeroProductStock(Connection conn, int productId) throws SQLException {
        return hasRows(conn,
            "SELECT COUNT(*) FROM products WHERE id = ? AND (quantity <> 0 OR reserved_quantity <> 0)",
            productId);
    }

    private boolean hasProductInInventoryLots(Connection conn, int productId) throws SQLException {
        return hasRows(conn, "SELECT COUNT(*) FROM inventory_lots WHERE product_id = ?", productId);
    }

    // Recheck immediately before deleting, including in the cleanup transaction,
    // so a stale dialog cannot erase on-hand stock or FIFO history.
    private void deleteProductIfEmpty(Connection conn, int productId) throws SQLException {
        String query = """
            DELETE FROM products
            WHERE id = ? AND quantity = 0 AND reserved_quantity = 0
              AND NOT EXISTS (SELECT 1 FROM inventory_lots WHERE product_id = ?)
            """;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, productId);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("Product has stock or FIFO history; deletion is blocked.");
            }
        }
    }

    private boolean hasProductInReservations(Connection conn, int productId) throws SQLException {
        return hasRows(conn, "SELECT COUNT(*) FROM stock_reservations WHERE product_id = ?", productId);
    }

    private boolean hasProductInNotifications(Connection conn, int productId) throws SQLException {
        return hasRows(conn, "SELECT COUNT(*) FROM warehouse_notifications WHERE product_id = ?", productId);
    }

    private boolean hasRows(Connection conn, String query, int productId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    
    private boolean hasProductInOrders(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM order_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInInvoices(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM invoice_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInSupplierOrders(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM supplier_order_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInSupplierLinks(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM product_suppliers WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInWarehouseMovements(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM warehouse_movements WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInMinStock(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM minimum_stock WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    // Helper methods to safely read integer and double values from ResultSet
    private Integer getIntegerFromResultSet(ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(strValue);
            }
            return null;
        } catch (SQLException | NumberFormatException e) {
            return null;
        }
    }

    private int getIntegerFromResultSet(ResultSet rs, String columnName, int defaultValue) {
        Integer value = getIntegerFromResultSet(rs, columnName);
        return value != null ? value : defaultValue;
    }

    private double getDoubleFromResultSet(ResultSet rs, String columnName, double defaultValue) {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) {
                    return defaultValue;
                }
                return Double.parseDouble(strValue);
            }
            return defaultValue;
        } catch (SQLException | NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Selects and highlights a specific product in the table by product code
     * @param productCode The code of the product to select
     */
    public void selectProductByCode(String productCode) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement p = conn.prepareStatement("SELECT id FROM products WHERE code=?")) {
                p.setString(1, productCode);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        selectProductById(r.getInt(1));
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this, "Error locating product: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Dialogs.showMessageDialog(this,
            "Product with code '" + productCode + "' not found in the list.",
            "Product Not Found", JOptionPane.INFORMATION_MESSAGE);
    }
}
