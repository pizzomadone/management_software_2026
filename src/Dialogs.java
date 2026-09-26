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

    private static Object message(Object value) {
        if (value instanceof String text) return I18n.tr(text);
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
        return JOptionPane.showInputDialog(parent, message(msg), I18n.tr(title), messageType,
      icon, choices(selectionValues), initialSelectionValue);
    }
}
