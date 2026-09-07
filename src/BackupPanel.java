import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupPanel extends JPanel {
    private final BackupManager backupManager = BackupManager.getInstance();
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private JTextField backupDirField;
    private JCheckBox autoBackupCheck;
    private JSpinner backupIntervalSpinner;
    private JSpinner retentionSpinner;
    private JTable backupsTable;
    private DefaultTableModel tableModel;

    public BackupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        initComponents();
        loadBackupsList();
    }

    private void initComponents() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Backup Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        backupDirField = new JTextField(backupManager.getBackupDirectory(), 24);
        configPanel.add(backupDirField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(event -> selectBackupDirectory());
        configPanel.add(browseButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        configPanel.add(new JLabel("Automatic Backup:"), gbc);
        gbc.gridx = 1;
        autoBackupCheck = new JCheckBox("Enable", backupManager.isAutoBackupEnabled());
        configPanel.add(autoBackupCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        configPanel.add(new JLabel("Backup Interval (hours):"), gbc);
        gbc.gridx = 1;
        backupIntervalSpinner = new JSpinner(new SpinnerNumberModel(
            backupManager.getBackupIntervalHours(), 1, 168, 1));
        configPanel.add(backupIntervalSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        configPanel.add(new JLabel("Retention Days:"), gbc);
        gbc.gridx = 1;
        retentionSpinner = new JSpinner(new SpinnerNumberModel(
            backupManager.getRetentionDays(), 1, 365, 1));
        configPanel.add(retentionSpinner, gbc);

        String[] columns = {"Registry ID", "Date", "File Name", "Size", "Path", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        backupsTable = new JTable(tableModel);
        backupsTable.removeColumn(backupsTable.getColumnModel().getColumn(0));
        TableSorterUtil.enableSorting(backupsTable, new int[]{0}, dateTimeFormat);
        backupsTable.getColumnModel().getColumn(3).setPreferredWidth(420);
        backupsTable.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        TableInteractionUtil.addDeleteKeyAction(backupsTable, this::deleteSelectedBackup);
        TableInteractionUtil.addContextMenu(backupsTable,
            new TableInteractionUtil.TableAction("Perform Backup", this::performBackup, false),
            new TableInteractionUtil.TableAction("Import Backup", this::importBackup, false),
            new TableInteractionUtil.TableAction("Restore Backup", this::restoreBackup),
            new TableInteractionUtil.TableAction("Delete Backup", this::deleteSelectedBackup),
            new TableInteractionUtil.TableAction("Remove from History", this::removeSelectedFromHistory),
            null,
            new TableInteractionUtil.TableAction("Refresh", this::loadBackupsList, false),
            new TableInteractionUtil.TableAction("Save Configuration", this::saveConfiguration, false));

        JPanel buttons = new JPanel(new FlowLayout());
        addButton(buttons, "Perform Backup", this::performBackup);
        addButton(buttons, "Import Backup", this::importBackup);
        addButton(buttons, "Restore Backup", this::restoreBackup);
        addButton(buttons, "Delete Backup", this::deleteSelectedBackup);
        addButton(buttons, "Remove from History", this::removeSelectedFromHistory);
        addButton(buttons, "Refresh", this::loadBackupsList);
        addButton(buttons, "Save Configuration", this::saveConfiguration);

        add(configPanel, BorderLayout.NORTH);
        add(new JScrollPane(backupsTable), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void addButton(JPanel panel, String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    private void selectBackupDirectory() {
        File selected = chooseDirectory("Select Backup Directory",
            new File(backupManager.getBackupDirectory()));
        if (selected != null) backupDirField.setText(selected.getAbsolutePath());
    }

    private File chooseDirectory(String title, File initialDirectory) {
        if (isMac()) {
            String property = "apple.awt.fileDialogForDirectories";
            String previous = System.getProperty(property);
            System.setProperty(property, "true");
            FileDialog dialog = new FileDialog(ownerFrame(), I18n.tr(title), FileDialog.LOAD);
            try {
                if (initialDirectory.isDirectory()) dialog.setDirectory(initialDirectory.getAbsolutePath());
                dialog.setVisible(true);
                return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
            } finally {
                dialog.dispose();
                if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
            }
        }
        JFileChooser chooser = new JFileChooser(initialDirectory);
        chooser.setDialogTitle(I18n.tr(title));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private File chooseBackupFile() {
        if (isMac()) {
            FileDialog dialog = new FileDialog(ownerFrame(), I18n.tr("Import Backup"), FileDialog.LOAD);
            dialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".db"));
            dialog.setVisible(true);
            try {
                return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
            } finally { dialog.dispose(); }
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.tr("Import Backup"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQLite (*.db)", "db"));
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private Frame ownerFrame() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        return owner instanceof Frame frame ? frame : null;
    }

    private void loadBackupsList() {
        tableModel.setRowCount(0);
        for (BackupManager.BackupRecord backup : backupManager.listBackupRecords()) {
            tableModel.addRow(new Object[]{backup.id(),
                dateTimeFormat.format(new Date(backup.createdAt())), backup.name(),
                String.format("%.2f MB", backup.size() / (1024.0 * 1024.0)),
                backup.absolutePath(), backup.exists() ? "Found" : "Not Found"});
        }
    }

    public void refreshBackups() {
        loadBackupsList();
    }

    private int selectedModelRow() {
        int selected = backupsTable.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, I18n.tr("Please select a backup"),
                I18n.tr("No Selection"), JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return backupsTable.convertRowIndexToModel(selected);
    }

    private void performBackup() {
        try {
            saveConfigurationValues();
            Path path = backupManager.performBackup();
            loadBackupsList();
            JOptionPane.showMessageDialog(this,
                I18n.tr("Backup performed successfully") + "\n" + I18n.tr("Path") + ": " + path,
                I18n.tr("Backup"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            showError("Error during backup", exception);
        }
    }

    private void importBackup() {
        File selected = chooseBackupFile();
        if (selected == null) return;
        try {
            backupManager.importBackup(selected.toPath());
            loadBackupsList();
            JOptionPane.showMessageDialog(this, I18n.tr("Backup imported successfully"),
                I18n.tr("Import Backup"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            showError("Unable to import backup", exception);
        }
    }

    private void restoreBackup() {
        int row = selectedModelRow();
        if (row < 0) return;
        Path path = Path.of((String) tableModel.getValueAt(row, 4));
        if (!Files.isRegularFile(path)) {
            JOptionPane.showMessageDialog(this, I18n.tr("Backup file not found"),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, I18n.tr("Are you sure you want to restore this backup?"),
                I18n.tr("Confirm Restore"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            backupManager.restoreBackup(path.toString());
            JOptionPane.showMessageDialog(this,
                I18n.tr("Backup restored successfully.") + "\n" + I18n.tr("The program will now restart."),
                I18n.tr("Restore Completed"), JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        } catch (Exception exception) {
            showError("Error during restore", exception);
        }
    }

    private void deleteSelectedBackup() {
        int row = selectedModelRow();
        if (row < 0) return;
        String id = (String) tableModel.getValueAt(row, 0);
        Path path = Path.of((String) tableModel.getValueAt(row, 4));
        if (JOptionPane.showConfirmDialog(this, I18n.tr("Delete the selected backup file and its history entry?"),
                I18n.tr("Confirm Deletion"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            if (Files.exists(path)) Files.delete(path);
            backupManager.removeBackupRecord(id);
            loadBackupsList();
            JOptionPane.showMessageDialog(this, I18n.tr("Backup deleted successfully"),
                I18n.tr("Success"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            showError("Error deleting backup", exception);
        }
    }

    private void removeSelectedFromHistory() {
        int row = selectedModelRow();
        if (row < 0) return;
        if (JOptionPane.showConfirmDialog(this, I18n.tr("Remove the selected backup from history?"),
                I18n.tr("Confirm Deletion"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        backupManager.removeBackupRecord((String) tableModel.getValueAt(row, 0));
        loadBackupsList();
    }

    private void saveConfigurationValues() {
        String directory = backupDirField.getText().trim();
        if (directory.isEmpty()) throw new IllegalArgumentException(I18n.tr("Backup directory is required"));
        backupManager.setBackupDirectory(directory);
        backupManager.setAutoBackupEnabled(autoBackupCheck.isSelected());
        backupManager.setBackupIntervalHours((Integer) backupIntervalSpinner.getValue());
        backupManager.setRetentionDays((Integer) retentionSpinner.getValue());
    }

    private void saveConfiguration() {
        try {
            saveConfigurationValues();
            JOptionPane.showMessageDialog(this, I18n.tr("Configuration saved successfully"),
                I18n.tr("Configuration"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            showError("Error saving configuration", exception);
        }
    }

    private void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(this, I18n.tr(message) + ": " + exception.getMessage(),
            I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, I18n.tr(String.valueOf(value)), selected, focus, row, column);
            if (!selected) label.setForeground("Found".equals(value) ? new Color(20, 130, 55) : Color.RED.darker());
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            return label;
        }
    }
}
