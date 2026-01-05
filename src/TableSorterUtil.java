import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
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
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);

        // Set default sort order to DESCENDING for all columns
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        for (int i = 0; i < table.getColumnCount(); i++) {
            sorter.setSortable(i, true);
        }

        // Configure sorter to start with DESCENDING order
        sorter.setSortsOnUpdates(true);
        sorter.setMaxSortKeys(1); // Only sort by one column at a time

        // Override the toggle sort order to go DESCENDING -> ASCENDING -> UNSORTED
        sorter.toggleSortOrder(0); // This will be overridden when user clicks
    }

    /**
     * Enables sorting on a JTable with custom configuration.
     * @param table The JTable to enable sorting on
     * @param sortableColumns Array of column indices that should be sortable (null for all)
     */
    public static void enableSorting(JTable table, int[] sortableColumns) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);

        if (sortableColumns != null) {
            // Disable sorting for all columns first
            for (int i = 0; i < table.getColumnCount(); i++) {
                sorter.setSortable(i, false);
            }
            // Enable only specified columns
            for (int col : sortableColumns) {
                sorter.setSortable(col, true);
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
