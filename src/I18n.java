import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Central runtime localization service backed by per-language resource bundles. */
public final class I18n {
    private static final Map<String, String> LANGUAGE_NAMES = new LinkedHashMap<>();
    private static final Map<Object, String> ORIGINAL_TEXT = new WeakHashMap<>();
    private static final Map<Object, String> LAST_TRANSLATION = new WeakHashMap<>();
    private static final Map<JComponent, String> ORIGINAL_TOOLTIPS = new WeakHashMap<>();
    private static final Map<JComponent, String> LAST_TOOLTIPS = new WeakHashMap<>();
    private static Map<String, String> translations = Map.of();
    private static String language = "it";
    private static boolean installed;
    private static final Set<Object> TEXT_WATCHED =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<JComponent> TOOLTIP_WATCHED =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> APPLYING_LOCALIZATION =
        ThreadLocal.withInitial(() -> false);

    static {
        LANGUAGE_NAMES.put("en", "English");
        LANGUAGE_NAMES.put("it", "Italiano");
        LANGUAGE_NAMES.put("es", "Español");
        LANGUAGE_NAMES.put("pt_BR", "Português (Brasil)");
        LANGUAGE_NAMES.put("fr", "Français");
        LANGUAGE_NAMES.put("de", "Deutsch");
        LANGUAGE_NAMES.put("pl", "Polski");
        loadTranslations();
    }

    private I18n() {}

    public static String[] languageNames() {
        return LANGUAGE_NAMES.values().toArray(String[]::new);
    }

    public static String codeForName(String name) {
        return LANGUAGE_NAMES.entrySet().stream()
            .filter(entry -> entry.getValue().equals(name))
            .map(Map.Entry::getKey).findFirst().orElse("it");
    }

    public static String nameForCode(String code) {
        return LANGUAGE_NAMES.getOrDefault(code, LANGUAGE_NAMES.get("it"));
    }

    public static String getLanguage() { return language; }

    public static void setLanguage(String code) {
        if (LANGUAGE_NAMES.containsKey(code)) {
            language = code;
            loadTranslations();
        }
        updateOptionPaneLabels();
        localizeOpenWindows();
    }

    private static void loadTranslations() {
        String[] parts = language.split("_", 2);
        Locale locale = parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
        Map<String, String> loaded = new HashMap<>();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            for (String key : bundle.keySet()) loaded.put(key, bundle.getString(key));
        } catch (MissingResourceException exception) {
            Path sourceCatalog = Path.of("resources", "i18n", "messages_" + language + ".properties");
            try (Reader reader = Files.newBufferedReader(sourceCatalog)) {
                ResourceBundle bundle = new PropertyResourceBundle(reader);
                for (String key : bundle.keySet()) loaded.put(key, bundle.getString(key));
            } catch (IOException missingSourceCatalog) {
                System.err.printf("Translation catalog for %s is unavailable; using source text.%n", language);
            }
        }
        translations = loaded;
    }

    public static String tr(String source) {
        if (source == null) return null;
        String exact = translations.get(source);
        if (exact != null) return exact;

        // Preserve UI punctuation while allowing catalogs to store reusable label keys.
        if (source.startsWith("* ")) {
            String translated = tr(source.substring(2));
            return translated.equals(source.substring(2)) ? source : "* " + translated;
        }
        if (source.endsWith(":")) {
            String base = source.substring(0, source.length() - 1).trim();
            String translated = translations.get(base);
            if (translated != null) return translated + ":";
        }
        if (source.endsWith("...")) {
            String base = source.substring(0, source.length() - 3).trim();
            String translated = translations.get(base);
            if (translated != null) return translated + "...";
        }

        // Translate only a registered prefix and preserve the dynamic business value verbatim.
        int separator = source.indexOf(": ");
        if (separator > 0 && separator < 80) {
            String prefix = source.substring(0, separator);
            String translated = translations.get(prefix);
            if (translated != null) return translated + ": " + source.substring(separator + 2);
        }

        return source;
    }

    private static String localized(Object owner, String current) {
        if (current == null) return null;
        String last = LAST_TRANSLATION.get(owner);
        if (!ORIGINAL_TEXT.containsKey(owner) || (last != null && !last.equals(current))) {
            ORIGINAL_TEXT.put(owner, current);
        }
        String translated = tr(ORIGINAL_TEXT.get(owner));
        LAST_TRANSLATION.put(owner, translated);
        return translated;
    }

    public static void install() {
        if (installed || GraphicsEnvironment.isHeadless()) return;
        installed = true;
        updateOptionPaneLabels();
        AWTEventListener listener = event -> {
            if (event instanceof WindowEvent windowEvent &&
                    windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                SwingUtilities.invokeLater(() -> localize(windowEvent.getWindow()));
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static void updateOptionPaneLabels() {
        UIManager.put("OptionPane.yesButtonText", tr("Yes"));
        UIManager.put("OptionPane.noButtonText", tr("No"));
        UIManager.put("OptionPane.cancelButtonText", tr("Cancel"));
        UIManager.put("OptionPane.okButtonText", tr("OK"));
    }

    public static void localizeOpenWindows() {
        if (GraphicsEnvironment.isHeadless()) return;
        for (Window window : Window.getWindows()) localize(window);
    }

    public static void localize(Component component) {
        if (component instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty("i18n.literal"))) return;
        if (component instanceof Frame frame) frame.setTitle(localized(frame, frame.getTitle()));
        if (component instanceof Dialog dialog) dialog.setTitle(localized(dialog, dialog.getTitle()));
        if (component instanceof AbstractButton button) {
            String originalButtonText = ORIGINAL_TEXT.getOrDefault(button, button.getText());
            button.setText(localized(button, button.getText()));
            watchText(button);
            if (button.getToolTipText() == null && originalButtonText != null &&
                    !originalButtonText.isBlank()) {
                String tooltipKey = "tooltip." + originalButtonText;
                ORIGINAL_TOOLTIPS.put(button, tooltipKey);
                String translatedTooltip = translations.getOrDefault(tooltipKey, tr(originalButtonText));
                LAST_TOOLTIPS.put(button, translatedTooltip);
                button.setToolTipText(translatedTooltip);
            }
        }
        if (component instanceof JLabel label) {
            label.setText(localized(label, label.getText()));
            watchText(label);
        }
        if (component instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                String key = "i18n.tab." + i;
                if (tabs.getClientProperty(key) == null) tabs.putClientProperty(key, tabs.getTitleAt(i));
                tabs.setTitleAt(i, tr((String) tabs.getClientProperty(key)));
            }
        }
        if (component instanceof JTable table) {
            for (Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
                    columns.hasMoreElements();) {
                TableColumn column = columns.nextElement();
                if (column.getHeaderValue() instanceof String value) {
                    column.setHeaderValue(localized(column, value));
                }
            }
            TableCellRenderer delegate = table.getDefaultRenderer(Object.class);
            if (!(delegate instanceof LocalizedTableRenderer)) {
                table.setDefaultRenderer(Object.class, new LocalizedTableRenderer(delegate));
            }
        }
        if (component instanceof JComboBox<?> comboBox &&
                !isInsideFileChooser(comboBox) &&
                !(comboBox.getRenderer() instanceof LocalizedListRenderer)) {
            installLocalizedListRenderer(comboBox);
        }
        // JMenu stores its entries in a separate popup, so they are not returned
        // by Container.getComponents(). Localize the actual drop-down contents.
        if (component instanceof JMenu menu) {
            for (Component menuComponent : menu.getMenuComponents()) localize(menuComponent);
        }
        if (component instanceof JComponent swingComponent) {
            localizeTooltip(swingComponent);
            Border border = swingComponent.getBorder();
            if (border instanceof TitledBorder titledBorder) {
                titledBorder.setTitle(localized(titledBorder, titledBorder.getTitle()));
            }
            // Context menus are also detached from the normal component tree.
            JPopupMenu popupMenu = swingComponent.getComponentPopupMenu();
            if (popupMenu != null) localize(popupMenu);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) localize(child);
        }
        component.revalidate();
        component.repaint();
    }

    private static void localizeTooltip(JComponent component) {
        watchTooltip(component);
        String current = component.getToolTipText();
        if (current == null || current.isBlank()) return;
        String last = LAST_TOOLTIPS.get(component);
        if (!ORIGINAL_TOOLTIPS.containsKey(component) || (last != null && !last.equals(current))) {
            ORIGINAL_TOOLTIPS.put(component, current);
        }
        String source = ORIGINAL_TOOLTIPS.get(component);
        String translated = source.startsWith("tooltip.")
            ? translations.getOrDefault(source, tr(source.substring("tooltip.".length())))
            : tr(source);
        LAST_TOOLTIPS.put(component, translated);
        component.setToolTipText(translated);
    }

    private static void watchText(Object component) {
        if (TEXT_WATCHED.contains(component)) return;
        TEXT_WATCHED.add(component);
        if (component instanceof AbstractButton button) {
            button.addPropertyChangeListener("text", event ->
                localizeChangedText(button, (String) event.getNewValue(), button::setText));
        } else if (component instanceof JLabel label) {
            label.addPropertyChangeListener("text", event ->
                localizeChangedText(label, (String) event.getNewValue(), label::setText));
        }
    }

    private static void localizeChangedText(Object owner, String source,
            java.util.function.Consumer<String> setter) {
        if (source == null || APPLYING_LOCALIZATION.get()) return;
        String previous = LAST_TRANSLATION.get(owner);
        if (previous != null && previous.equals(source)) return;
        ORIGINAL_TEXT.put(owner, source);
        String translated = tr(source);
        LAST_TRANSLATION.put(owner, translated);
        if (!translated.equals(source)) {
            APPLYING_LOCALIZATION.set(true);
            try { setter.accept(translated); }
            finally { APPLYING_LOCALIZATION.set(false); }
        }
    }

    private static void watchTooltip(JComponent component) {
        if (TOOLTIP_WATCHED.contains(component)) return;
        TOOLTIP_WATCHED.add(component);
        component.addPropertyChangeListener("toolTipText", event -> {
            String source = (String) event.getNewValue();
            if (source == null || APPLYING_LOCALIZATION.get()) return;
            String previous = LAST_TOOLTIPS.get(component);
            if (previous != null && previous.equals(source)) return;
            ORIGINAL_TOOLTIPS.put(component, source);
            String translated = tr(source);
            LAST_TOOLTIPS.put(component, translated);
            if (!translated.equals(source)) {
                APPLYING_LOCALIZATION.set(true);
                try { component.setToolTipText(translated); }
                finally { APPLYING_LOCALIZATION.set(false); }
            }
        });
    }

    private static boolean isInsideFileChooser(Component component) {
        return SwingUtilities.getAncestorOfClass(JFileChooser.class, component) != null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installLocalizedListRenderer(JComboBox<?> comboBox) {
        ListCellRenderer delegate = comboBox.getRenderer();
        if (delegate == null) delegate = new DefaultListCellRenderer();
        comboBox.setRenderer(new LocalizedListRenderer(delegate));
    }

    private static final class LocalizedListRenderer implements ListCellRenderer<Object> {
        private final ListCellRenderer<Object> delegate;

        @SuppressWarnings("unchecked")
        private LocalizedListRenderer(ListCellRenderer<?> delegate) {
            this.delegate = (ListCellRenderer<Object>) delegate;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            Object displayValue;
            if (value instanceof String text) {
                displayValue = tr(text);
            } else if (value instanceof javax.swing.filechooser.FileFilter filter) {
                displayValue = tr(filter.getDescription());
            } else {
                displayValue = value;
            }
            return delegate.getListCellRendererComponent(list, displayValue, index, selected, focus);
        }
    }

    private static final class LocalizedTableRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        private LocalizedTableRenderer(TableCellRenderer delegate) { this.delegate = delegate; }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            String heading = table.getModel().getColumnName(table.convertColumnIndexToModel(column));
            boolean enumColumn = java.util.Set.of("Status","Payment Status","Type","Reason","Priority").contains(heading);
            Object displayValue = enumColumn && value instanceof String text ? tr(text) : value;
            return delegate.getTableCellRendererComponent(table, displayValue,
                selected, focus, row, column);
        }
    }
}
