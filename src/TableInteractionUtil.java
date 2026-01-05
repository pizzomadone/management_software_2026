import javax.swing.*;
import java.awt.event.*;

/**
 * Utility class to add keyboard shortcuts and context menus to JTables.
 */
public class TableInteractionUtil {

    /**
     * Adds Delete key functionality to a table.
     * When Delete/Canc key is pressed on a selected row, executes the delete action.
     *
     * @param table The JTable to add the functionality to
     * @param deleteAction The action to execute when Delete is pressed (typically the delete button action)
     */
    public static void addDeleteKeyAction(JTable table, Runnable deleteAction) {
        // Add key binding for Delete key
        KeyStroke deleteKey = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(deleteKey, "delete");
        table.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getSelectedRow() != -1) {
                    deleteAction.run();
                }
            }
        });
    }

    /**
     * Adds a right-click context menu to a table.
     * Right-clicking on a row selects it and shows a popup menu with available actions.
     *
     * @param table The JTable to add the context menu to
     * @param actions Array of TableAction objects representing menu items
     */
    public static void addContextMenu(JTable table, TableAction... actions) {
        JPopupMenu contextMenu = new JPopupMenu();

        for (TableAction action : actions) {
            if (action == null) {
                // Add separator for null actions
                contextMenu.addSeparator();
            } else {
                JMenuItem menuItem = new JMenuItem(action.getName());
                menuItem.addActionListener(e -> {
                    if (action.getAction() != null) {
                        action.getAction().run();
                    }
                });
                // Set enabled state based on selection
                if (action.requiresSelection()) {
                    menuItem.setEnabled(table.getSelectedRow() != -1);
                }
                contextMenu.add(menuItem);
            }
        }

        // Add mouse listener for right-click
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            private void showMenu(MouseEvent e) {
                // Get the row at the click point
                int row = table.rowAtPoint(e.getPoint());

                // Select the row if it's not already selected
                if (row >= 0 && row < table.getRowCount()) {
                    table.setRowSelectionInterval(row, row);

                    // Update menu items' enabled state
                    for (int i = 0; i < contextMenu.getComponentCount(); i++) {
                        java.awt.Component comp = contextMenu.getComponent(i);
                        if (comp instanceof JMenuItem) {
                            JMenuItem item = (JMenuItem) comp;
                            // Find corresponding action
                            String itemText = item.getText();
                            for (TableAction action : actions) {
                                if (action != null && action.getName().equals(itemText)) {
                                    item.setEnabled(!action.requiresSelection() || table.getSelectedRow() != -1);
                                    break;
                                }
                            }
                        }
                    }

                    // Show the popup menu
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Represents an action that can be added to a table's context menu.
     */
    public static class TableAction {
        private final String name;
        private final Runnable action;
        private final boolean requiresSelection;

        /**
         * Creates a new table action.
         *
         * @param name The display name for the menu item
         * @param action The action to execute when the item is clicked
         * @param requiresSelection Whether this action requires a row to be selected
         */
        public TableAction(String name, Runnable action, boolean requiresSelection) {
            this.name = name;
            this.action = action;
            this.requiresSelection = requiresSelection;
        }

        /**
         * Creates a new table action that requires selection.
         *
         * @param name The display name for the menu item
         * @param action The action to execute when the item is clicked
         */
        public TableAction(String name, Runnable action) {
            this(name, action, true);
        }

        public String getName() { return name; }
        public Runnable getAction() { return action; }
        public boolean requiresSelection() { return requiresSelection; }
    }

    // Private constructor to prevent instantiation
    private TableInteractionUtil() {
        throw new AssertionError("TableInteractionUtil class cannot be instantiated");
    }
}
