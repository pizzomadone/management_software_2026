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
        if (translations.containsKey(source)) return translations.get(source);

        // Translate complete words/phrases in dynamic labels without repeatedly
        // translating the result. Plain String.replace() used to turn "Backup"
        // into values such as "Indietroup" because the key "Back" matched inside it.
        java.util.List<String> keys = translations.keySet().stream()
            .filter(key -> !key.isEmpty())
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        StringBuilder translated = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            String match = null;
            for (String key : keys) {
                if (source.startsWith(key, offset) && hasTranslationBoundaries(source, offset, key)) {
                    match = key;
                    break;
                }
            }
            if (match == null) {
                translated.append(source.charAt(offset++));
            } else {
                translated.append(translations.get(match));
                offset += match.length();
            }
        }
        return translated.toString();
    }

    private static boolean hasTranslationBoundaries(String source, int offset, String key) {
        int end = offset + key.length();
        boolean leftBoundary = !isTranslationWordCharacter(key.charAt(0)) || offset == 0 ||
            !isTranslationWordCharacter(source.charAt(offset - 1));
        boolean rightBoundary = !isTranslationWordCharacter(key.charAt(key.length() - 1)) ||
            end == source.length() || !isTranslationWordCharacter(source.charAt(end));
        return leftBoundary && rightBoundary;
    }

    private static boolean isTranslationWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
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
        if (component instanceof JFrame frame) frame.setTitle(localized(frame, frame.getTitle()));
        if (component instanceof JDialog dialog) dialog.setTitle(localized(dialog, dialog.getTitle()));
        if (component instanceof AbstractButton button) {
            String originalButtonText = ORIGINAL_TEXT.getOrDefault(button, button.getText());
            button.setText(localized(button, button.getText()));
            if (button.getToolTipText() == null && originalButtonText != null &&
                    !originalButtonText.isBlank()) {
                String tooltipKey = "tooltip." + originalButtonText;
                ORIGINAL_TOOLTIPS.put(button, tooltipKey);
                String translatedTooltip = translations.getOrDefault(tooltipKey, tr(originalButtonText));
                LAST_TOOLTIPS.put(button, translatedTooltip);
                button.setToolTipText(translatedTooltip);
            }
        }
        if (component instanceof JLabel label) label.setText(localized(label, label.getText()));
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
            Object displayValue = value instanceof String text ? tr(text) : value;
            return delegate.getTableCellRendererComponent(table, displayValue,
                selected, focus, row, column);
        }
    }
}
