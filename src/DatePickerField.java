import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;

/** Editable date field with a lightweight, localized calendar popup. */
public class DatePickerField extends JTextField {
    private final boolean allowEmpty;
    private final JPopupMenu popup = new JPopupMenu();
    private YearMonth displayedMonth;

    public DatePickerField(int columns) {
        this(null, columns, false);
    }

    public DatePickerField(String text) {
        this(text, 10, false);
    }

    public DatePickerField(int columns, boolean allowEmpty) {
        this(null, columns, allowEmpty);
    }

    public DatePickerField(String text, int columns, boolean allowEmpty) {
        super(text, columns);
        this.allowEmpty = allowEmpty;
        setToolTipText(I18n.tr("Open calendar"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled() && SwingUtilities.isLeftMouseButton(event)) showCalendar();
            }
        });
    }

    private void showCalendar() {
        LocalDate selected = parseCurrentDate();
        displayedMonth = YearMonth.from(selected != null ? selected : LocalDate.now());
        rebuildPopup(selected);
        popup.show(this, 0, getHeight());
    }

    private LocalDate parseCurrentDate() {
        String value = getText().trim();
        if (value.isEmpty()) return null;
        try {
            Date parsed = DateUtils.parseDate(value, DateUtils.DEFAULT_FORMAT);
            return Instant.ofEpochMilli(parsed.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (ParseException e) {
            return null;
        }
    }

    private void rebuildPopup(LocalDate selected) {
        popup.removeAll();
        JPanel calendar = new JPanel(new BorderLayout(4, 4));
        calendar.setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel navigation = new JPanel(new BorderLayout());
        JButton previous = new JButton("‹");
        JButton next = new JButton("›");
        JLabel month = new JLabel(monthLabel(), SwingConstants.CENTER);
        month.setFont(month.getFont().deriveFont(Font.BOLD));
        previous.setToolTipText(I18n.tr("Previous month"));
        next.setToolTipText(I18n.tr("Next month"));
        previous.addActionListener(event -> changeMonth(-1, selected));
        next.addActionListener(event -> changeMonth(1, selected));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(month, BorderLayout.CENTER);
        navigation.add(next, BorderLayout.EAST);
        calendar.add(navigation, BorderLayout.NORTH);

        JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));
        String[] weekdayKeys = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String key : weekdayKeys) {
            JLabel label = new JLabel(I18n.tr(key), SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            days.add(label);
        }

        int leadingDays = displayedMonth.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < leadingDays; i++) days.add(new JLabel());
        for (int day = 1; day <= displayedMonth.lengthOfMonth(); day++) {
            LocalDate date = displayedMonth.atDay(day);
            JButton dayButton = new JButton(String.valueOf(day));
            dayButton.setMargin(new Insets(2, 5, 2, 5));
            if (date.equals(selected)) dayButton.setFont(dayButton.getFont().deriveFont(Font.BOLD));
            dayButton.addActionListener(event -> selectDate(date));
            days.add(dayButton);
        }
        calendar.add(days, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (allowEmpty) {
            JButton clear = new JButton(I18n.tr("Clear"));
            clear.addActionListener(event -> { setText(""); popup.setVisible(false); });
            actions.add(clear);
        }
        JButton today = new JButton(I18n.tr("Today"));
        today.addActionListener(event -> selectDate(LocalDate.now()));
        actions.add(today);
        calendar.add(actions, BorderLayout.SOUTH);

        popup.add(calendar);
        I18n.localize(calendar);
        popup.pack();
    }

    private void changeMonth(int delta, LocalDate selected) {
        displayedMonth = displayedMonth.plusMonths(delta);
        rebuildPopup(selected);
    }

    private String monthLabel() {
        Locale locale = switch (I18n.getLanguage()) {
            case "es" -> Locale.of("es");
            case "pt_BR" -> Locale.of("pt", "BR");
            case "fr" -> Locale.of("fr");
            case "de" -> Locale.of("de");
            case "pl" -> Locale.of("pl");
            default -> Locale.of("it");
        };
        String name = displayedMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1) + " " + displayedMonth.getYear();
    }

    private void selectDate(LocalDate date) {
        Date value = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        setText(DateUtils.formatDate(value, DateUtils.DEFAULT_FORMAT));
        popup.setVisible(false);
        postActionEvent();
    }
}
