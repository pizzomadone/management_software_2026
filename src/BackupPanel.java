import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupPanel extends JPanel {
    private JTextField backupDirField;
    private JCheckBox autoBackupCheck;
    private JSpinner retentionSpinner;
    private JTable backupsTable;
    private DefaultTableModel tableModel;
    private BackupManager backupManager;

    public BackupPanel() {
        backupManager = BackupManager.getInstance();

        setupPanel();
        initComponents();
        loadBackupsList();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Configuration panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Backup directory
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Backup Directory:"), gbc);

        gbc.gridx = 1;
        backupDirField = new JTextField(backupManager.getBackupDirectory(), 20);
        configPanel.add(backupDirField, gbc);

        gbc.gridx = 2;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> selectBackupDirectory());
        configPanel.add(browseButton, gbc);

        // Auto backup
        gbc.gridx = 0; gbc.gridy = 1;
        configPanel.add(new JLabel("Automatic Backup:"), gbc);

        gbc.gridx = 1;
        autoBackupCheck = new JCheckBox("Enable", backupManager.isAutoBackupEnabled());
        configPanel.add(autoBackupCheck, gbc);

        // Retention days
        gbc.gridx = 0; gbc.gridy = 2;
        configPanel.add(new JLabel("Retention Days:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
            backupManager.getRetentionDays(), 1, 365, 1);
        retentionSpinner = new JSpinner(spinnerModel);
        configPanel.add(retentionSpinner, gbc);

        // Backup list
        String[] columns = {"Date", "File Name", "Size"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        backupsTable = new JTable(tableModel);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton backupButton = new JButton("Perform Backup");
        JButton restoreButton = new JButton("Restore Backup");
        JButton deleteButton = new JButton("Delete Backup");
        JButton saveButton = new JButton("Save Configuration");

        backupButton.addActionListener(e -> performBackup());
        restoreButton.addActionListener(e -> restoreBackup());
        deleteButton.addActionListener(e -> deleteSelectedBackup());
        saveButton.addActionListener(e -> saveConfiguration());

        buttonPanel.add(backupButton);
        buttonPanel.add(restoreButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(saveButton);

        // Main layout
        add(configPanel, BorderLayout.NORTH);
        add(new JScrollPane(backupsTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void selectBackupDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(backupManager.getBackupDirectory()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Backup Directory");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            backupDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadBackupsList() {
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        File[] backups = backupManager.listBackups();
        for (File backup : backups) {
            String[] row = {
                sdf.format(new Date(backup.lastModified())),
                backup.getName(),
                String.format("%.2f MB", backup.length() / (1024.0 * 1024.0))
            };
            tableModel.addRow(row);
        }
    }

    private void deleteSelectedBackup() {
        int selectedRow = backupsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "Please select a backup to delete",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = (String)tableModel.getValueAt(selectedRow, 1);

        int result = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete the backup:\n" + fileName + "?\n\n" +
            "This action cannot be undone.",
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            try {
                File backupFile = new File(backupManager.getBackupDirectory(), fileName);

                if (backupFile.exists() && backupFile.delete()) {
                    loadBackupsList();
                    JOptionPane.showMessageDialog(this,
                        "Backup deleted successfully",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to delete backup file",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error deleting backup: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performBackup() {
        try {
            backupManager.performBackup();
            loadBackupsList();
            JOptionPane.showMessageDialog(this,
                "Backup performed successfully",
                "Backup", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error during backup: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreBackup() {
        int selectedRow = backupsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "Select a backup to restore",
                "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = (String)tableModel.getValueAt(selectedRow, 1);
        String filePath = new File(backupManager.getBackupDirectory(), fileName).getAbsolutePath();

        int result = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to restore this backup?\n" +
            "This operation cannot be undone.",
            "Confirm Restore",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            try {
                backupManager.restoreBackup(filePath);
                JOptionPane.showMessageDialog(this,
                    "Backup restored successfully.\n" +
                    "The program will now restart.",
                    "Restore Completed",
                    JOptionPane.INFORMATION_MESSAGE);

                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error during restore: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveConfiguration() {
        try {
            backupManager.setBackupDirectory(backupDirField.getText());
            backupManager.setAutoBackupEnabled(autoBackupCheck.isSelected());
            backupManager.setRetentionDays((Integer)retentionSpinner.getValue());

            JOptionPane.showMessageDialog(this,
                "Configuration saved successfully",
                "Configuration", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving configuration: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
