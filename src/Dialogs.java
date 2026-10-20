import javax.swing.*;
import java.awt.*;

/** Centralized Swing dialogs: translate messages, titles and choices before Swing sizes the window. */
public final class Dialogs {
    private Dialogs() {}

    /**
     * Centers a window on its nearest visible owner. If no visible owner exists,
     * it centers on the usable bounds of the current/default screen.
     */
    public static void centerWindow(Window window) {
        if (window == null || GraphicsEnvironment.isHeadless()) return;

        Window owner = window.getOwner();
        while (owner != null && !owner.isShowing()) owner = owner.getOwner();

        if (owner == null) {
            for (Window candidate : Window.getWindows()) {
                if (candidate == window || !candidate.isShowing()) continue;
                if (candidate.isActive()) {
                    owner = candidate;
                    break;
                }
            }
        }

        Rectangle usable;
        if (owner != null && owner.getGraphicsConfiguration() != null) {
            GraphicsConfiguration gc = owner.getGraphicsConfiguration();
            Rectangle bounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            usable = new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
        } else {
            usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        }

        Dimension size = window.getSize();
        int x;
        int y;
        if (owner != null) {
            Rectangle b = owner.getBounds();
            x = b.x + (b.width - size.width) / 2;
            y = b.y + (b.height - size.height) / 2;
        } else {
            x = usable.x + (usable.width - size.width) / 2;
            y = usable.y + (usable.height - size.height) / 2;
        }

        int maxX = usable.x + Math.max(0, usable.width - size.width);
        int maxY = usable.y + Math.max(0, usable.height - size.height);
        x = Math.max(usable.x, Math.min(x, maxX));
        y = Math.max(usable.y, Math.min(y, maxY));
        window.setLocation(x, y);
    }

    /**
     * Small dashboard-style question-mark help. The explanation opens on click
     * as a lightweight tooltip popup and closes when the pointer leaves the icon.
     */
    public static JLabel clickHelpLabel(String tooltipHtml) {
        JLabel help = new JLabel("?", SwingConstants.CENTER) {
            private Popup helpPopup;

            {
                putClientProperty("i18n.literal", true);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addHierarchyListener(event -> {
                    if (!isShowing()) hideHelpPopup();
                });
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) showHelpPopup();
                    }

                    @Override public void mouseExited(java.awt.event.MouseEvent event) {
                        hideHelpPopup();
                    }
                });
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                return null;
            }

            private void showHelpPopup() {
                hideHelpPopup();
                String text = getToolTipText();
                if (text == null || text.isBlank() || !isShowing()) return;

                JToolTip tip = createToolTip();
                tip.setTipText(text);
                Dimension size = tip.getPreferredSize();
                Point location;
                try {
                    location = getLocationOnScreen();
                } catch (IllegalComponentStateException exception) {
                    return;
                }

                int x = location.x + getWidth() - size.width;
                int y = location.y + getHeight() + 4;
                helpPopup = PopupFactory.getSharedInstance().getPopup(this, tip, x, y);
                helpPopup.show();
            }

            private void hideHelpPopup() {
                if (helpPopup != null) {
                    helpPopup.hide();
                    helpPopup = null;
                }
            }
        };

        help.setToolTipText(tooltipHtml);
        help.setFont(help.getFont().deriveFont(Font.BOLD, 10f));
        Color foreground = UIManager.getColor("Label.disabledForeground");
        help.setForeground(foreground != null ? foreground : Color.GRAY);
        Color border = UIManager.getColor("Separator.foreground");
        help.setBorder(BorderFactory.createLineBorder(border != null ? border : Color.LIGHT_GRAY, 1, true));
        help.setPreferredSize(new Dimension(16, 16));
        help.setMinimumSize(new Dimension(16, 16));
        help.setMaximumSize(new Dimension(16, 16));
        return help;
    }

    /** Creates the standard legend used by forms that contain required fields. */
    public static JPanel requiredLegendPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel("* Required fields");
        label.setFont(label.getFont().deriveFont(Font.ITALIC));
        label.setForeground(Color.GRAY);
        panel.add(label);
        return panel;
    }

    private static Object message(Object value) {
        if (value instanceof String text) {
            String translated = I18n.tr(text);
            // JOptionPane does not wrap a long file path or exception message.
            // Keep long messages readable and inside the usable screen area.
            int longestLine = 0;
            for (String line : translated.split("\\n", -1))
                longestLine = Math.max(longestLine, line.length());
            if (longestLine <= 90 && translated.length() <= 240
                    || GraphicsEnvironment.isHeadless())
                return translated;

            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
            int width = Math.max(180, Math.min(560, screen.width - 140));
            JTextArea area = new JTextArea(translated);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(UIManager.getFont("OptionPane.messageFont"));
            area.setOpaque(false);
            area.setSize(new Dimension(width, 2000));
            int height = Math.max(45, Math.min(area.getPreferredSize().height + 8,
                Math.max(80, Math.min(300, screen.height - 170))));
            JScrollPane pane = new JScrollPane(area,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            pane.setBorder(BorderFactory.createEmptyBorder());
            pane.setPreferredSize(new Dimension(width, height));
            return pane;
        }
        if (value instanceof Object[] values) {
  Object[] translated = values.clone();
  for (int i = 0; i < translated.length; i++) translated[i] = message(translated[i]);
  return translated;
        }
        if (value instanceof Component component) I18n.localize(component);
        return value;
    }

    private static Object[] choices(Object[] values) {
        if (values == null) return null;
        Object[] translated = values.clone();
        for (int i = 0; i < translated.length; i++) translated[i] = message(translated[i]);
        return translated;
    }

    public static void showMessageDialog(Component parent, Object msg) {
        JOptionPane.showMessageDialog(parent, message(msg));
    }
    public static void showMessageDialog(Component parent, Object msg, String title, int type) {
        JOptionPane.showMessageDialog(parent, message(msg), I18n.tr(title), type);
    }
    public static void showMessageDialog(Component parent, Object msg, String title, int type, Icon icon) {
        JOptionPane.showMessageDialog(parent, message(msg), I18n.tr(title), type, icon);
    }

    public static int showConfirmDialog(Component parent, Object msg) {
        return JOptionPane.showConfirmDialog(parent, message(msg));
    }
    public static int showConfirmDialog(Component parent, Object msg, String title, int optionType) {
        return JOptionPane.showConfirmDialog(parent, message(msg), I18n.tr(title), optionType);
    }
    public static int showConfirmDialog(Component parent, Object msg, String title, int optionType, int messageType) {
        return JOptionPane.showConfirmDialog(parent, message(msg), I18n.tr(title), optionType, messageType);
    }
    public static int showConfirmDialog(Component parent, Object msg, String title, int optionType, int messageType, Icon icon) {
        return JOptionPane.showConfirmDialog(parent, message(msg), I18n.tr(title), optionType, messageType, icon);
    }

    public static int showOptionDialog(Component parent, Object msg, String title, int optionType,
                             int messageType, Icon icon, Object[] options, Object initialValue) {
        Object[] translated = choices(options);
        Object initial = initialValue instanceof String s ? I18n.tr(s) : initialValue;
        if (translated != null && options != null && initialValue != null) {
  for (int i = 0; i < options.length; i++) {
      if (java.util.Objects.equals(options[i], initialValue)) {
          initial = translated[i];
          break;
      }
  }
        }
        return JOptionPane.showOptionDialog(parent, message(msg), I18n.tr(title), optionType,
      messageType, icon, translated, initial);
    }

    public static String showInputDialog(Object msg) {
        return JOptionPane.showInputDialog(message(msg));
    }
    public static String showInputDialog(Object msg, Object initialValue) {
        return JOptionPane.showInputDialog(message(msg), initialValue);
    }
    public static String showInputDialog(Component parent, Object msg) {
        return JOptionPane.showInputDialog(parent, message(msg));
    }
    public static String showInputDialog(Component parent, Object msg, Object initialValue) {
        return JOptionPane.showInputDialog(parent, message(msg), initialValue);
    }
    public static String showInputDialog(Component parent, Object msg, String title, int messageType) {
        return JOptionPane.showInputDialog(parent, message(msg), I18n.tr(title), messageType);
    }
    public static Object showInputDialog(Component parent, Object msg, String title, int messageType,
                               Icon icon, Object[] selectionValues, Object initialSelectionValue) {
        Object[] translated = choices(selectionValues);
        Object initial = initialSelectionValue instanceof String s ? I18n.tr(s) : initialSelectionValue;
        if (translated != null && selectionValues != null && initialSelectionValue != null) {
            for (int i = 0; i < selectionValues.length; i++) {
                if (java.util.Objects.equals(selectionValues[i], initialSelectionValue)) {
                    initial = translated[i];
                    break;
                }
            }
        }
        return JOptionPane.showInputDialog(parent, message(msg), I18n.tr(title), messageType,
            icon, translated, initial);
    }
}
