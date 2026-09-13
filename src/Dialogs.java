import javax.swing.*;
import java.awt.Component;

/** Centralized Swing dialogs: translate messages, titles and choices before Swing sizes the window. */
public final class Dialogs {
    private Dialogs() {}

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
