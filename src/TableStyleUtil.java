import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.event.TableModelEvent;
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

    /**
     * Keeps naturally short columns compact and gives unused width to fields
     * that benefit from space (address, notes, descriptions, e-mail, names).
     * Product table intentionally does not use this path.
     */
    private static void installSmartColumnSizing(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty(SMART_SIZING_INSTALLED))) return;
        table.putClientProperty(SMART_SIZING_INSTALLED, Boolean.TRUE);

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

            int min = compactMinimumWidth(normalizedHeader, headerMetrics, header);
            int natural = Math.max(min, headerMetrics.stringWidth(header) + 14);

            int modelColumn = column.getModelIndex();
            int rowLimit = Math.min(table.getRowCount(), 160);
            for (int row = 0; row < rowLimit; row++) {
                Object value = table.getModel().getValueAt(
                    table.convertRowIndexToModel(row), modelColumn);
                if (value == null || value instanceof Icon) continue;
                String text = String.valueOf(value).trim();
                if (text.isEmpty()) continue;
                natural = Math.max(natural, cellMetrics.stringWidth(text) + 12);
            }

            natural = Math.min(natural, compactNaturalCap(normalizedHeader));
            widths[view] = Math.max(min, natural);
            minimums[view] = min;
            weights[view] = flexibleColumnWeight(normalizedHeader);
            total += widths[view];
        }

        int grid = Math.max(0, count - 1) * Math.max(0, table.getIntercellSpacing().width);
        int available = Math.max(1, viewportWidth - grid);

        if (total < available) {
            distributeExtraWidth(widths, weights, available - total);
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
        if (isIdHeader(header)) return Math.max(42, Math.min(58, headerWidth));
        if (isDateHeader(header)) return Math.max(92, Math.min(122, headerWidth));
        if (isQuantityOrMoneyHeader(header)) return Math.max(76, Math.min(118, headerWidth));
        if (isCodeHeader(header)) return Math.max(86, Math.min(145, headerWidth));
        if (isStatusHeader(header)) return Math.max(90, Math.min(125, headerWidth));
        return Math.max(72, Math.min(150, headerWidth));
    }

    private static int compactNaturalCap(String header) {
        if (isIdHeader(header)) return 62;
        if (isDateHeader(header)) return 128;
        if (isQuantityOrMoneyHeader(header)) return 132;
        if (isCodeHeader(header)) return 165;
        if (isStatusHeader(header)) return 140;
        if (containsAny(header, "phone", "telefono", "téléphone", "telefon")) return 175;
        if (containsAny(header, "vat", "iva", "partita iva", "tax")) return 165;
        if (isVeryFlexibleTextHeader(header)) return 360;
        if (isFlexibleTextHeader(header)) return 260;
        return 220;
    }

    private static void distributeExtraWidth(int[] widths, int[] weights, int extra) {
        int totalWeight = 0;
        for (int weight : weights) totalWeight += Math.max(0, weight);

        if (totalWeight == 0) {
            for (int i = 0; i < weights.length; i++) weights[i] = 1;
            totalWeight = weights.length;
        }

        int remaining = extra;
        for (int i = 0; i < widths.length; i++) {
            if (weights[i] <= 0) continue;
            int add = (int)Math.floor((double)extra * weights[i] / totalWeight);
            widths[i] += add;
            remaining -= add;
        }

        // Put rounding remainder into the most useful flexible columns first.
        while (remaining-- > 0) {
            int best = 0;
            for (int i = 1; i < weights.length; i++) {
                if (weights[i] > weights[best]) best = i;
            }
            widths[best]++;
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
        if (isIdHeader(header) || isDateHeader(header) || isQuantityOrMoneyHeader(header)
                || isCodeHeader(header) || isStatusHeader(header)) return 0;
        if (isVeryFlexibleTextHeader(header)) return 6;
        if (containsAny(header, "email", "e-mail")) return 4;
        if (isFlexibleTextHeader(header)) return 3;
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

    private static final class SmartCellRenderer extends DefaultTableCellRenderer {
        private final JTable styledTable;

        SmartCellRenderer(JTable table) {
            styledTable = table;
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            String fullText = value == null ? "" : String.valueOf(value);
            super.getTableCellRendererComponent(table, fullText, selected, focus, row, column);

            String header = visibleHeader(table, column);
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
                String fullText = value == null ? "" : String.valueOf(value);
                label.setHorizontalAlignment(alignmentFor(visibleHeader(table, column), value));
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
                String fullText = value == null ? "" : String.valueOf(value).trim();
                if ((label.getToolTipText() == null || label.getToolTipText().isBlank())
                        && !fullText.isBlank()) {
                    int available = Math.max(8,
                        table.getColumnModel().getColumn(column).getWidth()
                            - label.getInsets().left - label.getInsets().right - 2);
                    if (label.getFontMetrics(label.getFont()).stringWidth(fullText) > available) {
                        label.setToolTipText(fullText);
                    }
                }
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
        if (isCenteredDataHeader(h)) return SwingConstants.CENTER;
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
