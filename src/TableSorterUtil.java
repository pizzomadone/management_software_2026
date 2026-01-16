import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Utility class to enable column sorting on JTables.
 * First click sorts descending (largest to smallest), second click sorts ascending.
 */
public class TableSorterUtil {

    /**
     * Enables sorting on a JTable with descending sort as default.
     * @param table The JTable to enable sorting on
     */
    public static void enableSorting(JTable table) {
        enableSorting(table, null, null);
    }

    /**
     * Enables sorting on a JTable with custom configuration.
     * @param table The JTable to enable sorting on
     * @param sortableColumns Array of column indices that should be sortable (null for all)
     */
    public static void enableSorting(JTable table, int[] sortableColumns) {
        enableSorting(table, sortableColumns, null, null);
    }

    /**
     * Enables sorting on a JTable with date column support.
     * @param table The JTable to enable sorting on
     * @param dateColumns Array of column indices that contain dates
     * @param dateFormat The date format used for the date columns
     */
    public static void enableSorting(JTable table, int[] dateColumns, SimpleDateFormat dateFormat) {
        enableSorting(table, null, dateColumns, dateFormat);
    }

    /**
     * Enables sorting on a JTable with full custom configuration.
     * @param table The JTable to enable sorting on
     * @param sortableColumns Array of column indices that should be sortable (null for all)
     * @param dateColumns Array of column indices that contain dates (null for none)
     * @param dateFormat The date format used for the date columns
     */
    public static void enableSorting(JTable table, int[] sortableColumns, int[] dateColumns, SimpleDateFormat dateFormat) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);

        // Configure sortable columns
        if (sortableColumns != null) {
            // Disable sorting for all columns first
            for (int i = 0; i < table.getColumnCount(); i++) {
                sorter.setSortable(i, false);
            }
            // Enable only specified columns
            for (int col : sortableColumns) {
                sorter.setSortable(col, true);
            }
        } else {
            // Enable all columns by default
            for (int i = 0; i < table.getColumnCount(); i++) {
                sorter.setSortable(i, true);
            }
        }

        // Set custom comparator for date columns
        if (dateColumns != null && dateFormat != null) {
            Comparator<String> dateComparator = new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    // Handle null or empty strings
                    if ((s1 == null || s1.isEmpty() || s1.equals("N/A")) &&
                        (s2 == null || s2.isEmpty() || s2.equals("N/A"))) {
                        return 0;
                    }
                    if (s1 == null || s1.isEmpty() || s1.equals("N/A")) {
                        return 1; // Null values go to the end
                    }
                    if (s2 == null || s2.isEmpty() || s2.equals("N/A")) {
                        return -1; // Null values go to the end
                    }

                    try {
                        Date date1 = DateUtils.parseDate(s1, dateFormat);
                        Date date2 = DateUtils.parseDate(s2, dateFormat);

                        if (date1 == null && date2 == null) return 0;
                        if (date1 == null) return 1;
                        if (date2 == null) return -1;

                        return date1.compareTo(date2);
                    } catch (Exception e) {
                        // If parsing fails, fall back to string comparison
                        return s1.compareTo(s2);
                    }
                }
            };

            for (int col : dateColumns) {
                sorter.setComparator(col, dateComparator);
            }
        }

        sorter.setSortsOnUpdates(true);
        sorter.setMaxSortKeys(1);
    }

    // Private constructor to prevent instantiation
    private TableSorterUtil() {
        throw new AssertionError("TableSorterUtil class cannot be instantiated");
    }
}
