import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/** Small reusable search field wrapper with an integrated clear button. */
public final class SearchFieldUtil {
    private SearchFieldUtil() {}

    public static JComponent withClearButton(JTextField field, Runnable onClear) {
        Dimension preferred = field.getPreferredSize();
        Border fieldBorder = field.getBorder();

        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(field.getBackground());
        wrapper.setBorder(fieldBorder);
        wrapper.setPreferredSize(preferred);

        field.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));

        JButton clear = new JButton("×");
        clear.setFocusable(false);
        clear.setContentAreaFilled(false);
        clear.setOpaque(false);
        clear.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 6));
        clear.setMargin(new Insets(0, 0, 0, 0));
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.setToolTipText(I18n.tr("Clear search"));
        clear.setVisible(!field.getText().isEmpty());

        Runnable updateVisibility = () ->
            clear.setVisible(!field.getText().isEmpty());

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateVisibility.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateVisibility.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateVisibility.run(); }
        });

        clear.addActionListener(e -> {
            if (field.getText().isEmpty()) return;
            field.setText("");
            field.requestFocusInWindow();
            if (onClear != null) onClear.run();
        });

        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(clear, BorderLayout.EAST);
        return wrapper;
    }
}
