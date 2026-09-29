import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.event.TableModelEvent;
import java.util.Arrays;
import java.util.Locale;

/** Shared visual treatment for WareStat tables. */
public final class TableStyleUtil {
    private static final int COMPACT_HORIZONTAL_PADDING = 4;
    private static final int COMPACT_VERTICAL_PADDING = 1;

    // Canonical palette: copied from the Supplier Orders table version approved
    // in commit a8206287. Keep these values in sync with that visual reference.
    private static final int LIGHT_GRID_DELTA = -6;
    private static final int DARK_GRID_DELTA = 8;
    private static final int LIGHT_ZEBRA_DELTA = -5;
    private static final int DARK_ZEBRA_DELTA = 7;
    private static final int LIGHT_HEADER_DELTA = -7;
    private static final int DARK_HEADER_DELTA = 10;
    private static final int LIGHT_HEADER_LINE_DELTA = -22;
    private static final int DARK_HEADER_LINE_DELTA = 22;

    private TableStyleUtil() {}

    /**
     * Standard WareStat table style: compact rows, very light grid, zebra rows,
     * centered headers, smart alignment, badges and clipped-text tooltips.
     */
    public static void applyCompact(JTable table) {
        if (table == null) return;

        // Body text stays regular; only the header carries emphasis.
        table.setFont(table.getFont().deriveFont(Font.PLAIN));

        table.setRowHeight(Math.max(24, table.getFontMetrics(table.getFont()).getHeight() + 5));
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setGridColor(adjustColor(table.getBackground(),
            isLight(table.getBackground()) ? LIGHT_GRID_DELTA : DARK_GRID_DELTA));
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        styleHeader(table, false);

        SmartCellRenderer renderer = new SmartCellRenderer(table);
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Number.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
        table.setDefaultRenderer(Long.class, renderer);
        table.setDefaultRenderer(Double.class, renderer);
        table.setDefaultRenderer(Float.class, renderer);

        // Apply alignment per visible column, not only through JTable defaults.
        // This also covers tables that already have a custom column renderer.
        TableColumnModel columns = table.getColumnModel();
        for (int view = 0; view < columns.getColumnCount(); view++) {
            TableColumn column = columns.getColumn(view);
            String header = headerText(column);
            if (isBadgeColumn(header)) {
                column.setCellRenderer(new BadgeRenderer(table));
                continue;
            }

            TableCellRenderer delegate = column.getCellRenderer();
            if (delegate == null) {
                Class<?> type = table.getModel().getColumnClass(column.getModelIndex());
                delegate = table.getDefaultRenderer(type);
            }
            column.setCellRenderer(new CompactColumnRenderer(table, delegate));
        }

        installSmartColumnSizing(table);
    }

    /**
     * Product table keeps its existing row height, padding, grid and alignments.
     * Only zebra, improved header, appropriate badges and ellipsis/tooltips are added.
     */
    public static void applyProductEnhancements(JTable table) {
        if (table == null) return;

        // Keep the existing product-table geometry while using regular body text.
        table.setFont(table.getFont().deriveFont(Font.PLAIN));

        // Product cells keep their existing geometry/alignment, but the header
        // must be graphically identical to every other WareStat table.
        styleHeader(table, false);
        TableColumnModel columns = table.getColumnModel();
        for (int view = 0; view < columns.getColumnCount(); view++) {
            TableColumn column = columns.getColumn(view);
            TableCellRenderer delegate = column.getCellRenderer();
            if (delegate == null) {
                Class<?> type = table.getModel().getColumnClass(column.getModelIndex());
                delegate = table.getDefaultRenderer(type);
            }

            String header = headerText(column);
            if (isBadgeColumn(header)) {
                column.setCellRenderer(new ProductBadgeRenderer(table, delegate));
            } else {
                column.setCellRenderer(new ProductDecoratingRenderer(table, delegate));
            }
        }
    }

    private static void styleHeader(JTable table, boolean preserveAlignment) {
        JTableHeader header = table.getTableHeader();
        if (header == null) return;

        TableCellRenderer delegate = header.getDefaultRenderer();
        Font currentFont = header.getFont();
        header.setFont(currentFont.deriveFont(Font.BOLD));
        if (!preserveAlignment) {
            Dimension preferred = header.getPreferredSize();
            header.setPreferredSize(new Dimension(preferred.width, 28));
        }

        Color headerBackground = adjustColor(table.getBackground(),
            isLight(table.getBackground()) ? LIGHT_HEADER_DELTA : DARK_HEADER_DELTA);
        Color headerLine = adjustColor(table.getBackground(),
            isLight(table.getBackground()) ? LIGHT_HEADER_LINE_DELTA : DARK_HEADER_LINE_DELTA);

        header.setDefaultRenderer((tbl, value, selected, focus, row, column) -> {
            Component component = delegate.getTableCellRendererComponent(
                tbl, value, selected, focus, row, column);
            component.setFont(header.getFont());
            component.setBackground(headerBackground);
            if (component instanceof JLabel label) {
                if (!preserveAlignment) label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, headerLine),
                    BorderFactory.createEmptyBorder(0, 4, 0, 4)));
            }
            return component;
        });
    }

    private static void installBadges(JTable table, boolean preserveAlignment) {
        TableColumnModel columns = table.getColumnModel();
        for (int view = 0; view < columns.getColumnCount(); view++) {
            TableColumn column = columns.getColumn(view);
            if (!isBadgeColumn(headerText(column))) continue;
            TableCellRenderer delegate = column.getCellRenderer();
            if (preserveAlignment && delegate != null) {
                column.setCellRenderer(new ProductBadgeRenderer(table, delegate));
            } else {
                column.setCellRenderer(new BadgeRenderer(table));
            }
        }
    }

    private static final String SMART_SIZING_INSTALLED = "warestat.smartColumnSizingInstalled";
    private static final String SMART_SIZING_PENDING = "warestat.smartColumnSizingPending";
    private static final String COLUMN_PERSISTENCE_INSTALLED =
        "warestat.columnPersistenceInstalled";
    private static final String COLUMN_PERSISTENCE_KEY =
        "warestat.columnPersistenceKey";
    private static final String CURRENCY_CODE_PROPERTY = "warestat.currencyCode";

    /** Optional document-specific currency (for example an issued invoice). */
    public static void setCurrencyCode(JTable table, String currencyCode) {
        if (table == null) return;
        table.putClientProperty(CURRENCY_CODE_PROPERTY,
            currencyCode == null || currencyCode.isBlank() ? null : currencyCode);
    }

    /**
     * Optional explicit persistence key for tables that need to share or isolate
     * column layouts. Normally WareStat derives a stable key automatically.
     */
    public static void setColumnPersistenceKey(JTable table, String key) {
        if (table == null) return;
        table.putClientProperty(COLUMN_PERSISTENCE_KEY,
            key == null || key.isBlank() ? null : key.trim());
    }

    /**
     * Keeps naturally short columns compact and gives unused width to fields
     * that benefit from space (address, notes, descriptions, e-mail, names).
     * Product table intentionally does not use this path.
     */
    /** Applies only automatic column-width balancing, without changing table colors or renderers. */
    public static void applySmartColumnSizing(JTable table) {
        if (table == null) return;
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        installSmartColumnSizing(table);
    }

    private static void installSmartColumnSizing(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty(SMART_SIZING_INSTALLED))) return;
        table.putClientProperty(SMART_SIZING_INSTALLED, Boolean.TRUE);

        installColumnLayoutPersistence(table);

        table.getModel().addTableModelListener(event -> {
            // Refit after rows are loaded/removed, but do not move columns around
            // while a user is editing an ordinary cell value.
            if (event.getType() != TableModelEvent.UPDATE
                    || event.getFirstRow() == TableModelEvent.HEADER_ROW) {
                scheduleSmartColumnSizing(table);
            }
        });

        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                scheduleSmartColumnSizing(table);
            }
        });

        scheduleSmartColumnSizing(table);
    }

    private static void scheduleSmartColumnSizing(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty(SMART_SIZING_PENDING))) return;
        table.putClientProperty(SMART_SIZING_PENDING, Boolean.TRUE);
        SwingUtilities.invokeLater(() -> {
            table.putClientProperty(SMART_SIZING_PENDING, Boolean.FALSE);
            fitCompactColumns(table);
        });
    }

    private static void fitCompactColumns(JTable table) {
        if (table.getColumnCount() == 0) return;

        JViewport viewport = (JViewport)SwingUtilities.getAncestorOfClass(
            JViewport.class, table);
        int viewportWidth = viewport == null
            ? table.getWidth() : viewport.getExtentSize().width;
        if (viewportWidth <= 0) return;

        TableColumnModel columns = table.getColumnModel();
        int count = columns.getColumnCount();
        int[] widths = new int[count];
        int[] minimums = new int[count];
        int[] maximums = new int[count];
        int[] weights = new int[count];

        FontMetrics headerMetrics = table.getTableHeader() == null
            ? table.getFontMetrics(table.getFont())
            : table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        FontMetrics cellMetrics = table.getFontMetrics(table.getFont());

        int total = 0;
        for (int view = 0; view < count; view++) {
            TableColumn column = columns.getColumn(view);
            String header = headerText(column);
            String normalizedHeader = normalized(header);

            // Preserve deliberately hidden columns.
            if (column.getMaxWidth() == 0) {
                widths[view] = 0;
                minimums[view] = 0;
                maximums[view] = 0;
                weights[view] = 0;
                continue;
            }

            int min = compactMinimumWidth(normalizedHeader, headerMetrics, header);
            int natural = Math.max(min, headerMetrics.stringWidth(header) + 14);

            int modelColumn = column.getModelIndex();
            int rowLimit = Math.min(table.getRowCount(), 160);
            for (int row = 0; row < rowLimit; row++) {
                Object value = table.getModel().getValueAt(
                    table.convertRowIndexToModel(row), modelColumn);
                if (value == null || value instanceof Icon) continue;
                String text = displayText(table, view, value);
                if (text.isEmpty()) continue;
                natural = Math.max(natural, cellMetrics.stringWidth(text) + 12);
            }

            natural = Math.min(natural, compactNaturalCap(normalizedHeader));
            widths[view] = Math.max(min, natural);
            minimums[view] = min;
            maximums[view] = Math.max(widths[view], compactMaximumWidth(normalizedHeader));
            weights[view] = flexibleColumnWeight(normalizedHeader);
            total += widths[view];
        }

        int[] persisted = loadPersistedColumnWidths(table);
        if (persisted != null) {
            total = 0;
            for (int view = 0; view < count; view++) {
                TableColumn column = columns.getColumn(view);
                int modelColumn = column.getModelIndex();
                if (modelColumn < 0 || modelColumn >= persisted.length
                        || persisted[modelColumn] <= 0 || maximums[view] == 0) {
                    total += widths[view];
                    continue;
                }

                int saved = Math.max(minimums[view], persisted[modelColumn]);
                widths[view] = saved;
                // A user's deliberate width is allowed to exceed the automatic cap.
                maximums[view] = Math.max(maximums[view], saved);
                total += saved;
            }
        }

        int grid = Math.max(0, count - 1) * Math.max(0, table.getIntercellSpacing().width);
        int available = Math.max(1, viewportWidth - grid);

        if (total < available) {
            distributeExtraWidth(widths, maximums, weights, available - total);
        } else if (total > available) {
            shrinkToFit(widths, minimums, weights, total - available);
        }

        for (int view = 0; view < count; view++) {
            TableColumn column = columns.getColumn(view);
            column.setMinWidth(minimums[view]);
            column.setPreferredWidth(widths[view]);
            column.setWidth(widths[view]);
        }
        table.doLayout();
    }

    private static int compactMinimumWidth(String header, FontMetrics metrics, String rawHeader) {
        int headerWidth = metrics.stringWidth(rawHeader) + 12;
        if (isIdHeader(header)) return Math.max(44, Math.min(60, headerWidth));
        if (isDateHeader(header)) return Math.max(102, Math.min(132, headerWidth));
        if (isInventoryValueHeader(header)) return Math.max(150, Math.min(205, headerWidth));
        if (isQuantityOrMoneyHeader(header)) return Math.max(84, Math.min(128, headerWidth));
        if (isCodeHeader(header)) return Math.max(96, Math.min(155, headerWidth));
        if (isStatusHeader(header)) return Math.max(98, Math.min(135, headerWidth));
        if (isEntityReferenceHeader(header)) return Math.max(110, Math.min(180, headerWidth));
        return Math.max(76, Math.min(155, headerWidth));
    }

    private static int compactNaturalCap(String header) {
        if (isIdHeader(header)) return 64;
        if (isDateHeader(header)) return 138;
        if (isInventoryValueHeader(header)) return 220;
        if (isQuantityOrMoneyHeader(header)) return 148;
        if (isCodeHeader(header)) return 180;
        if (isStatusHeader(header)) return 150;
        if (isPaymentHeader(header)) return 300;
        if (isEntityReferenceHeader(header)) return 250;
        if (containsAny(header, "phone", "telefono", "téléphone", "telefon")) return 185;
        if (containsAny(header, "vat", "iva", "partita iva", "tax")) return 175;
        if (isVeryFlexibleTextHeader(header)) return 390;
        if (containsAny(header, "email", "e-mail")) return 300;
        if (isFlexibleTextHeader(header)) return 250;
        return 230;
    }

    private static int compactMaximumWidth(String header) {
        if (isIdHeader(header)) return 68;
        if (isDateHeader(header)) return 146;
        if (isInventoryValueHeader(header)) return 250;
        if (isQuantityOrMoneyHeader(header)) return 160;
        if (isCodeHeader(header)) return 195;
        if (isStatusHeader(header)) return 160;
        if (isPaymentHeader(header)) return 390;
        if (isEntityReferenceHeader(header)) return 320;
        if (containsAny(header, "email", "e-mail")) return 360;
        if (isVeryFlexibleTextHeader(header)) return 480;
        if (isFlexibleTextHeader(header)) return 320;
        return 280;
    }

    private static void distributeExtraWidth(int[] widths, int[] maximums,
            int[] weights, int extra) {
        int remaining = extra;

        // First grow every column only up to a sensible semantic maximum.
        while (remaining > 0) {
            int totalWeight = 0;
            for (int i = 0; i < widths.length; i++) {
                if (weights[i] > 0 && widths[i] < maximums[i]) totalWeight += weights[i];
            }
            if (totalWeight == 0) break;

            int distributed = 0;
            for (int i = 0; i < widths.length; i++) {
                if (weights[i] <= 0 || widths[i] >= maximums[i]) continue;
                int share = Math.max(1,
                    (int)Math.floor((double)remaining * weights[i] / totalWeight));
                int add = Math.min(share, maximums[i] - widths[i]);
                widths[i] += add;
                distributed += add;
                if (distributed >= remaining) break;
            }
            if (distributed <= 0) break;
            remaining -= distributed;
        }

        if (remaining <= 0) return;

        // Any truly surplus width goes only to the most useful text field(s),
        // not to compact columns such as ID/date/amount/status.
        int bestWeight = 0;
        for (int weight : weights) bestWeight = Math.max(bestWeight, weight);
        if (bestWeight <= 0) {
            int visible = 0;
            for (int max : maximums) if (max > 0) visible++;
            if (visible == 0) return;
            for (int i = 0; i < widths.length && remaining > 0; i++) {
                if (maximums[i] <= 0) continue;
                int add = Math.max(1, remaining / visible--);
                widths[i] += add;
                remaining -= add;
            }
            return;
        }

        int candidates = 0;
        for (int weight : weights) if (weight == bestWeight) candidates++;
        for (int i = 0; i < widths.length && remaining > 0; i++) {
            if (weights[i] != bestWeight) continue;
            int add = Math.max(1, remaining / candidates--);
            widths[i] += add;
            remaining -= add;
        }
    }

    private static void shrinkToFit(int[] widths, int[] minimums, int[] weights, int overflow) {
        int remaining = overflow;

        // First reclaim space from wide/flexible text fields, preserving compact
        // numeric/date/code columns at their natural sizes as long as possible.
        while (remaining > 0) {
            int best = -1;
            int bestRoom = 0;
            int bestWeight = -1;
            for (int i = 0; i < widths.length; i++) {
                int room = widths[i] - minimums[i];
                if (room <= 0) continue;
                if (weights[i] > bestWeight || (weights[i] == bestWeight && room > bestRoom)) {
                    best = i;
                    bestRoom = room;
                    bestWeight = weights[i];
                }
            }
            if (best < 0) break;

            int step = Math.min(remaining, Math.max(1, bestRoom / 3));
            widths[best] -= step;
            remaining -= step;
        }
    }

    private static int flexibleColumnWeight(String header) {
        if (isIdHeader(header)) return 0;
        // Compact fields may grow a little, but can never consume the table.
        if (isDateHeader(header) || isQuantityOrMoneyHeader(header)
                || isCodeHeader(header) || isStatusHeader(header)) return 1;
        if (isVeryFlexibleTextHeader(header)) return 6;
        if (isPaymentHeader(header)) return 5;
        if (containsAny(header, "email", "e-mail")) return 4;
        if (isEntityReferenceHeader(header)) return 2;
        if (isFlexibleTextHeader(header)) return 2;
        return 1;
    }

    private static boolean isIdHeader(String header) {
        return header.equals("id") || header.endsWith(" id") || header.startsWith("id ")
            || header.contains(" id ") || header.equals("#");
    }

    private static boolean isDateHeader(String header) {
        return containsAny(header, "date", "data", "fecha", "datum");
    }

    private static boolean isCodeHeader(String header) {
        return containsAny(header,
            "code", "codice", "código", "codigo", "kod", "sku",
            "number", "numero", "número", "numéro", "nummer", "document",
            "documento", "dokument", "vat no", "partita iva");
    }

    private static boolean isInventoryValueHeader(String header) {
        return containsAny(header,
            "stock at last purchase cost",
            "warehouse at last purchase cost",
            "warehouse value at last purchase cost");
    }

    private static boolean isQuantityOrMoneyHeader(String header) {
        return containsAny(header,
            "qty", "quantity", "quantità", "quantita", "cantidad", "quantidade",
            "quantité", "menge", "ilość", "ilosc",
            "price", "prezzo", "precio", "preço", "preco", "prix", "preis", "cena",
            "total", "totale", "gesamt", "suma", "cost", "costo", "coût", "cout",
            "kosten", "koszt", "revenue", "ricavi", "amount", "importo", "importe",
            "vat", "iva", "tax", "stock", "physical", "reserved", "available",
            "minimum", "min ", "%");
    }

    private static boolean isStatusHeader(String header) {
        return isBadgeColumn(header);
    }

    private static boolean isEntityReferenceHeader(String header) {
        return containsAny(header,
            "customer", "cliente", "client", "kunde", "klient",
            "supplier", "fornitore", "proveedor", "fornecedor", "fournisseur",
            "lieferant", "dostawca",
            "product", "prodotto", "producto", "produto", "produit", "produkt");
    }

    private static boolean isPaymentHeader(String header) {
        return containsAny(header,
            "payment", "pagamento", "pago", "paiement", "zahlung", "płatność", "platnosc");
    }

    private static boolean isVeryFlexibleTextHeader(String header) {
        return containsAny(header,
            "address", "indirizzo", "direccion", "dirección", "endereço", "endereco",
            "adresse", "adres", "notes", "note", "notas", "description", "descrizione",
            "descripción", "descricao", "description", "beschreibung", "opis",
            "message", "messaggio", "mensaje", "mensagem", "nachricht", "wiadomość",
            "path", "percorso", "chemin", "pfad", "ścieżka");
    }

    private static boolean isFlexibleTextHeader(String header) {
        return containsAny(header,
            "name", "nome", "ragione sociale", "company", "azienda",
            "product", "prodotto", "customer", "cliente", "supplier", "fornitore",
            "email", "e-mail", "reason", "motivo", "source", "origine", "fonte",
            "file", "category", "categoria");
    }

    private static void installColumnLayoutPersistence(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty(COLUMN_PERSISTENCE_INSTALLED))) return;
        table.putClientProperty(COLUMN_PERSISTENCE_INSTALLED, Boolean.TRUE);

        JTableHeader header = table.getTableHeader();
        if (header == null) return;

        restorePersistedColumnOrder(table);

        // Header release happens once after a resize or reorder gesture, avoiding
        // disk writes for every pixel while the divider/column is being dragged.
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                SwingUtilities.invokeLater(() -> {
                    savePersistedColumnWidths(table);
                    savePersistedColumnOrder(table);
                });
            }
        });
    }

    private static void savePersistedColumnWidths(JTable table) {
        if (table == null || table.getColumnCount() == 0) return;

        int modelCount = table.getModel().getColumnCount();
        int[] widths = new int[modelCount];
        Arrays.fill(widths, -1);

        TableColumnModel columns = table.getColumnModel();
        for (int view = 0; view < columns.getColumnCount(); view++) {
            TableColumn column = columns.getColumn(view);
            int modelColumn = column.getModelIndex();
            if (modelColumn < 0 || modelColumn >= widths.length || column.getMaxWidth() == 0)
                continue;
            widths[modelColumn] = Math.max(1, column.getWidth());
        }

        StringBuilder value = new StringBuilder();
        for (int model = 0; model < widths.length; model++) {
            if (widths[model] <= 0) continue;
            if (value.length() > 0) value.append(';');
            value.append(model).append('=').append(widths[model]);
        }
        if (value.length() == 0) return;

        SettingsPanel.setGlobalSetting(columnPersistenceSettingKey(table), value.toString());
    }

    private static int[] loadPersistedColumnWidths(JTable table) {
        if (table == null || table.getModel().getColumnCount() == 0) return null;

        String raw = SettingsPanel.getGlobalSetting(
            columnPersistenceSettingKey(table), "").trim();
        if (raw.isEmpty()) return null;

        int[] widths = new int[table.getModel().getColumnCount()];
        boolean found = false;
        try {
            for (String entry : raw.split(";")) {
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator >= entry.length() - 1) continue;
                int model = Integer.parseInt(entry.substring(0, separator).trim());
                int width = Integer.parseInt(entry.substring(separator + 1).trim());
                if (model < 0 || model >= widths.length || width <= 0) continue;
                widths[model] = width;
                found = true;
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return found ? widths : null;
    }

    private static void savePersistedColumnOrder(JTable table) {
        if (table == null || table.getColumnCount() == 0) return;

        StringBuilder value = new StringBuilder();
        TableColumnModel columns = table.getColumnModel();
        for (int view = 0; view < columns.getColumnCount(); view++) {
            int model = columns.getColumn(view).getModelIndex();
            if (model < 0) continue;
            if (value.length() > 0) value.append(',');
            value.append(model);
        }
        if (value.length() == 0) return;

        SettingsPanel.setGlobalSetting(columnOrderPersistenceSettingKey(table),
            value.toString());
    }

    private static void restorePersistedColumnOrder(JTable table) {
        String raw = SettingsPanel.getGlobalSetting(
            columnOrderPersistenceSettingKey(table), "").trim();
        if (raw.isEmpty()) return;

        TableColumnModel columns = table.getColumnModel();
        int count = columns.getColumnCount();
        String[] entries = raw.split(",");
        if (entries.length != count) return;

        int[] desired = new int[count];
        boolean[] seen = new boolean[Math.max(count, table.getModel().getColumnCount())];
        try {
            for (int i = 0; i < count; i++) {
                int model = Integer.parseInt(entries[i].trim());
                if (model < 0 || model >= seen.length || seen[model]) return;
                if (table.convertColumnIndexToView(model) < 0) return;
                seen[model] = true;
                desired[i] = model;
            }
        } catch (NumberFormatException exception) {
            return;
        }

        for (int targetView = 0; targetView < count; targetView++) {
            int currentView = table.convertColumnIndexToView(desired[targetView]);
            if (currentView < 0) return;
            if (currentView != targetView)
                table.moveColumn(currentView, targetView);
        }
    }

    private static String columnOrderPersistenceSettingKey(JTable table) {
        String widthKey = columnPersistenceSettingKey(table);
        return widthKey.replaceFirst("^table\\.column_widths\\.",
            "table.column_order.");
    }

    private static String columnPersistenceSettingKey(JTable table) {
        Object explicit = table.getClientProperty(COLUMN_PERSISTENCE_KEY);
        if (explicit instanceof String text && !text.isBlank())
            return "table.column_widths." + sanitizeSettingKey(text);

        String owner = tableOwnerName(table);
        StringBuilder signature = new StringBuilder();
        for (int model = 0; model < table.getModel().getColumnCount(); model++) {
            if (model > 0) signature.append('|');
            String name = table.getModel().getColumnName(model);
            signature.append(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        }

        String hash = Integer.toUnsignedString(signature.toString().hashCode(), 36);
        return "table.column_widths." + sanitizeSettingKey(owner) + "." + hash;
    }

    private static String tableOwnerName(JTable table) {
        Component current = table.getParent();
        while (current != null) {
            Class<?> type = current.getClass();
            String className = type.getName();
            if (!className.startsWith("javax.swing.")
                    && !className.startsWith("java.awt.")
                    && !type.isAnonymousClass()
                    && !type.isSynthetic()) {
                String simple = type.getSimpleName();
                if (simple != null && !simple.isBlank()) return simple;
            }
            current = current.getParent();
        }
        return "table";
    }

    private static String sanitizeSettingKey(String value) {
        String sanitized = value == null ? "" :
            value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return sanitized.isBlank() ? "table" : sanitized;
    }

    private static final class SmartCellRenderer extends DefaultTableCellRenderer {
        private final JTable styledTable;

        SmartCellRenderer(JTable table) {
            styledTable = table;
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            String fullText = displayText(table, column, value);
            super.getTableCellRendererComponent(table, fullText, selected, focus, row, column);

            String header = sourceHeader(table, column);
            setHorizontalAlignment(alignmentFor(header, value));
            setVerticalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(
                COMPACT_VERTICAL_PADDING, COMPACT_HORIZONTAL_PADDING,
                COMPACT_VERTICAL_PADDING, COMPACT_HORIZONTAL_PADDING));
            setBackground(selected ? table.getSelectionBackground() : rowBackground(styledTable, row));
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());

            int available = Math.max(8,
                table.getColumnModel().getColumn(column).getWidth()
                    - (COMPACT_HORIZONTAL_PADDING * 2) - 2);
            FontMetrics metrics = getFontMetrics(getFont());
            String display = ellipsize(fullText, metrics, available);
            setText(display);
            setToolTipText(display.equals(fullText) || fullText.isBlank() ? null : fullText);
            return this;
        }
    }

    private static final class CompactColumnRenderer implements TableCellRenderer {
        private final JTable styledTable;
        private final TableCellRenderer delegate;

        CompactColumnRenderer(JTable table, TableCellRenderer delegate) {
            styledTable = table;
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            Component component = delegate.getTableCellRendererComponent(
                table, value, selected, focus, row, column);

            if (component instanceof JComponent jc) jc.setOpaque(true);
            component.setBackground(selected
                ? table.getSelectionBackground() : rowBackground(styledTable, row));
            if (!selected) component.setForeground(table.getForeground());

            if (component instanceof JLabel label) {
                String fullText = displayText(table, column, value);
                label.setHorizontalAlignment(alignmentFor(sourceHeader(table, column), value));
                label.setVerticalAlignment(SwingConstants.CENTER);
                label.setBorder(BorderFactory.createEmptyBorder(
                    COMPACT_VERTICAL_PADDING, COMPACT_HORIZONTAL_PADDING,
                    COMPACT_VERTICAL_PADDING, COMPACT_HORIZONTAL_PADDING));

                int available = Math.max(8,
                    table.getColumnModel().getColumn(column).getWidth()
                        - (COMPACT_HORIZONTAL_PADDING * 2) - 2);
                FontMetrics metrics = label.getFontMetrics(label.getFont());
                String display = ellipsize(fullText, metrics, available);
                label.setText(display);
                label.setToolTipText(display.equals(fullText) || fullText.isBlank()
                    ? null : fullText);
            }
            return component;
        }
    }

    private static final class BadgeRenderer extends JPanel implements TableCellRenderer {
        private final JTable styledTable;
        private final JLabel badge = new JLabel("", SwingConstants.CENTER);

        BadgeRenderer(JTable table) {
            super(new FlowLayout(FlowLayout.CENTER, 0, 2));
            styledTable = table;
            setOpaque(true);
            badge.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            String text = value == null ? "" : String.valueOf(value);
            BadgeColors colors = badgeColors(text);

            removeAll();
            setBackground(selected ? table.getSelectionBackground() : rowBackground(styledTable, row));
            badge.setText(text);
            badge.setBackground(colors.background());
            badge.setForeground(colors.foreground());
            badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(adjustColor(colors.background(),
                    isLight(colors.background()) ? -16 : 16)),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)));
            badge.setToolTipText(text.isBlank() ? null : text);
            add(badge);
            return this;
        }
    }

    private static final class ProductDecoratingRenderer implements TableCellRenderer {
        private final JTable styledTable;
        private final TableCellRenderer delegate;

        ProductDecoratingRenderer(JTable table, TableCellRenderer delegate) {
            styledTable = table;
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            Component component = delegate.getTableCellRendererComponent(
                table, value, selected, focus, row, column);
            if (component instanceof JComponent jc) jc.setOpaque(true);
            component.setBackground(selected
                ? table.getSelectionBackground() : rowBackground(styledTable, row));
            if (!selected) component.setForeground(table.getForeground());

            if (component instanceof JLabel label) {
                String fullText = displayText(table, column, value).trim();
                int available = Math.max(8,
                    table.getColumnModel().getColumn(column).getWidth()
                        - label.getInsets().left - label.getInsets().right - 2);
                FontMetrics metrics = label.getFontMetrics(label.getFont());
                String display = ellipsize(fullText, metrics, available);
                label.setText(display);
                label.setToolTipText(display.equals(fullText) || fullText.isBlank()
                    ? null : fullText);
            }
            return component;
        }
    }

    private static final class ProductBadgeRenderer implements TableCellRenderer {
        private final JTable styledTable;
        private final TableCellRenderer delegate;

        ProductBadgeRenderer(JTable table, TableCellRenderer delegate) {
            styledTable = table;
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            Component base = delegate.getTableCellRendererComponent(
                table, value, selected, focus, row, column);
            int alignment = base instanceof JLabel label
                ? label.getHorizontalAlignment() : SwingConstants.LEFT;
            String text = value == null ? "" : String.valueOf(value);
            BadgeColors colors = badgeColors(text);

            JPanel panel = new JPanel(new FlowLayout(flowAlignment(alignment), 0, 0));
            panel.setOpaque(true);
            panel.setBackground(selected
                ? table.getSelectionBackground() : rowBackground(styledTable, row));

            JLabel badge = new JLabel(text);
            badge.setOpaque(true);
            badge.setFont(base.getFont());
            badge.setForeground(colors.foreground());
            badge.setBackground(colors.background());
            badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(adjustColor(colors.background(),
                    isLight(colors.background()) ? -16 : 16)),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
            badge.setToolTipText(text.isBlank() ? null : text);
            panel.add(badge);
            return panel;
        }
    }

    private static int flowAlignment(int swingAlignment) {
        if (swingAlignment == SwingConstants.CENTER) return FlowLayout.CENTER;
        if (swingAlignment == SwingConstants.RIGHT || swingAlignment == SwingConstants.TRAILING)
            return FlowLayout.RIGHT;
        return FlowLayout.LEFT;
    }

    private static int alignmentFor(String header, Object value) {
        if (value instanceof Number) return SwingConstants.CENTER;

        String text = value == null ? "" : String.valueOf(value).trim();
        if (looksLikeNumberOrDate(text)) return SwingConstants.CENTER;

        String h = normalized(header);
        if (isCenteredDataHeader(h) || isEntityReferenceHeader(h))
            return SwingConstants.CENTER;
        return SwingConstants.LEFT;
    }

    private static boolean isCenteredDataHeader(String h) {
        if (h.equals("#") || h.equals("id") || h.equals("%")
                || h.endsWith(" id") || h.startsWith("id ") || h.contains(" id "))
            return true;

        return containsAny(h,
            // Codes / identifiers
            "code", "codice", "código", "codigo", "kod", "sku",
            "number", "numero", "número", "numéro", "nummer", "nr",
            " no.", " n.", "n°", "nº",
            "document", "documento", "dokument",
            // Dates
            "date", "data", "fecha", "datum",
            // Quantities / numeric measures
            "qty", "quantity", "quantità", "quantita", "cantidad", "quantidade",
            "quantité", "menge", "ilość", "ilosc",
            "price", "prezzo", "precio", "preço", "preco", "prix", "preis", "cena",
            "total", "totale", "gesamt", "suma",
            "cost", "costo", "coût", "cout", "kosten", "koszt",
            "revenue", "ricavi", "ingresos", "receita", "umsatz", "przychód", "przychod",
            "amount", "importo", "importe", "montant", "betrag", "kwota",
            "vat", "iva", "tva", "mwst", "tax", "imposta", "impuesto", "taxe",
            "steuer", "podatek",
            "weight", "peso", "poids", "gewicht", "waga",
            "size", "dimensione", "tamaño", "tamano", "tamanho", "taille", "größe",
            "grosse", "rozmiar",
            "stock", "scorta", "existencia", "estoque", "lagermenge", "stan",
            "physical", "fisico", "físico", "physique", "physisch", "fizyczny",
            "reserved", "riservato", "reservado", "réservé", "reserve", "reserviert",
            "zarezerwowany",
            "available", "disponibile", "disponible", "verfügbar", "verfugbar", "dostęp",
            "on order", "in ordine", "en pedido", "em pedido", "en commande",
            "bestellt", "w zamówieniu", "w zamowieniu",
            "minimum", "minimo", "mínimo", "mindest", "minimalny",
            "products", "prodotti", "productos", "produtos", "produits", "produkte",
            "produkty", "%");
    }

    private static boolean looksLikeNumberOrDate(String text) {
        if (text.isEmpty()) return false;

        if (text.matches("\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?"))
            return true;

        // Numeric, monetary and percentage strings, including localized separators.
        return text.matches("[+\\-]?\\s*[€$£¥]?\\s*\\d[\\d\\s.,']*(?:\\s*[%€$£¥])?");
    }

    private static boolean isBadgeColumn(String header) {
        String h = normalized(header);
        return h.equals("status") || h.equals("active") || h.equals("state")
            || h.equals("stato") || h.equals("attivo") || h.equals("etat")
            || h.equals("état") || h.equals("aktiv") || h.equals("estado")
            || h.equals("status");
    }

    private static BadgeColors badgeColors(String value) {
        String text = normalized(value);

        // Negative states first: words such as "inactive" contain "active".
        if (containsAny(text, "cancelled", "canceled", "annullato", "inactive",
                "inattivo", "missing", "error", "out of stock", "overdue", "no",
                "nein", "non", "nie")) {
            return new BadgeColors(new Color(247, 226, 226), new Color(145, 54, 54));
        }
        if (containsAny(text, "completed", "completato", "complete",
                "paid", "active", "attivo", "found", "handled", "read", "delivered",
                "in stock", "yes", "si", "sí", "oui", "ja", "tak")) {
            return new BadgeColors(new Color(224, 242, 229), new Color(34, 105, 55));
        }
        if (containsAny(text, "confirmed", "confermato", "open", "approved")) {
            return new BadgeColors(new Color(225, 236, 250), new Color(40, 82, 135));
        }
        if (containsAny(text, "in transit", "in transito", "pending", "low stock",
                "warning", "waiting")) {
            return new BadgeColors(new Color(252, 238, 215), new Color(145, 90, 18));
        }
        return new BadgeColors(new Color(235, 235, 235), new Color(85, 85, 85));
    }

    private static String displayText(JTable table, int viewColumn, Object value) {
        if (value == null) return "";

        String header = sourceHeader(table, viewColumn);
        if (!isMonetaryHeader(header)) return String.valueOf(value);

        Double numeric = numericValue(value);
        if (numeric == null) return String.valueOf(value);

        Object property = table.getClientProperty(CURRENCY_CODE_PROPERTY);
        String currencyCode = property instanceof String text && !text.isBlank()
            ? text : SettingsPanel.getCurrencyCode();

        return isUnitMonetaryHeader(header)
            ? SettingsPanel.formatUnitCurrency(numeric, currencyCode)
            : SettingsPanel.formatCurrency(numeric, currencyCode);
    }

    private static Double numericValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (!(value instanceof String text)) return null;
        String trimmed = text.trim();
        if (!trimmed.matches("[+\\-]?\\d+(?:[.,]\\d+)?")) return null;
        try { return Double.parseDouble(trimmed.replace(',', '.')); }
        catch (NumberFormatException exception) { return null; }
    }

    private static boolean isMonetaryHeader(String header) {
        String h = normalized(header);
        if (h.isEmpty()) return false;
        if (h.contains("%") || containsAny(h, "rate", "aliquota", "tasso")) return false;

        if (h.equals("vat") || h.equals("iva") || h.equals("tva") || h.equals("mwst"))
            return true;

        return containsAny(h,
            "price", "prezzo", "precio", "preço", "preco", "prix", "preis", "cena",
            "cost", "costo", "coût", "cout", "kosten", "koszt",
            "total", "totale", "gesamt", "suma",
            "revenue", "ricavi", "ingresos", "receita", "umsatz", "przychód", "przychod",
            "sales", "vendite", "ventas", "vendas", "ventes", "umsätze", "umsatze",
            "profit", "utile", "beneficio", "lucro", "bénéfice", "benefice", "gewinn", "zysk",
            "amount", "importo", "importe", "montant", "betrag", "kwota");
    }

    private static boolean isUnitMonetaryHeader(String header) {
        String h = normalized(header);
        if (containsAny(h,
                "historical cost", "costo storico", "coût historique", "cout historique",
                "gross profit", "utile lordo", "total", "totale", "gesamt", "suma",
                "revenue", "ricavi", "sales", "vendite", "profit", "utile",
                "amount", "importo", "taxable", "imponibile", "stock at"))
            return false;

        return containsAny(h,
            "price", "prezzo", "precio", "preço", "preco", "prix", "preis", "cena",
            "unit cost", "costo unitario", "purchase cost", "costo acquisto",
            "last purchase cost", "purchase price");
    }

    private static String sourceHeader(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnModel().getColumnCount()) return "";
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        if (modelColumn < 0 || modelColumn >= table.getModel().getColumnCount()) return "";
        String name = table.getModel().getColumnName(modelColumn);
        return name == null ? "" : name;
    }

    private static String visibleHeader(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnModel().getColumnCount()) return "";
        return headerText(table.getColumnModel().getColumn(viewColumn));
    }

    private static String headerText(TableColumn column) {
        Object value = column.getHeaderValue();
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static Color rowBackground(JTable table, int row) {
        Color base = table.getBackground();
        if ((row & 1) == 0) return base;
        return adjustColor(base,
            isLight(base) ? LIGHT_ZEBRA_DELTA : DARK_ZEBRA_DELTA);
    }

    private static boolean isLight(Color color) {
        return color.getRed() + color.getGreen() + color.getBlue() >= 384;
    }

    private static Color adjustColor(Color color, int amount) {
        return new Color(
            Math.max(0, Math.min(255, color.getRed() + amount)),
            Math.max(0, Math.min(255, color.getGreen() + amount)),
            Math.max(0, Math.min(255, color.getBlue() + amount)));
    }

    private static String ellipsize(String text, FontMetrics metrics, int availableWidth) {
        if (text == null || text.isEmpty() || metrics.stringWidth(text) <= availableWidth)
            return text == null ? "" : text;

        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        if (suffixWidth >= availableWidth) return suffix;

        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, mid).stripTrailing() + suffix;
            if (metrics.stringWidth(candidate) <= availableWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, low).stripTrailing() + suffix;
    }

    private record BadgeColors(Color background, Color foreground) {}
}
