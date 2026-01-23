/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dialog for displaying and accepting the software license agreement.
 * This dialog is shown on first run and requires user acceptance to proceed.
 */
public class LicenseAcceptanceDialog extends JDialog {

    private boolean accepted = false;
    private static final String ACCEPTANCE_FILE = "license_accepted.flag";

    public LicenseAcceptanceDialog(Frame parent) {
        super(parent, "License Agreement", true);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("Software License Agreement");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);

        // License text area
        JTextArea licenseTextArea = new JTextArea(LicenseText.getSoftwareLicense());
        licenseTextArea.setEditable(false);
        licenseTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        licenseTextArea.setLineWrap(true);
        licenseTextArea.setWrapStyleWord(true);
        licenseTextArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(licenseTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        // Instructions panel
        JPanel instructionsPanel = new JPanel(new BorderLayout());
        instructionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JLabel instructionsLabel = new JLabel(
            "<html><b>Please read the license agreement carefully.</b><br>" +
            "You must accept the terms to use this software.</html>"
        );
        instructionsPanel.add(instructionsLabel, BorderLayout.CENTER);

        // Bottom panel with buttons
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        bottomPanel.add(instructionsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton declineButton = new JButton("Decline");
        declineButton.setPreferredSize(new Dimension(100, 30));
        declineButton.addActionListener(e -> {
            accepted = false;
            int result = JOptionPane.showConfirmDialog(
                this,
                "If you decline the license agreement, the application will close.\n" +
                "Are you sure you want to decline?",
                "Decline License",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.YES_OPTION) {
                dispose();
                System.exit(0);
            }
        });

        JButton acceptButton = new JButton("Accept");
        acceptButton.setPreferredSize(new Dimension(100, 30));
        acceptButton.setBackground(new Color(0, 120, 215));
        acceptButton.setForeground(Color.WHITE);
        acceptButton.setFocusPainted(false);
        acceptButton.addActionListener(e -> {
            accepted = true;
            saveLicenseAcceptance();
            dispose();
        });

        buttonPanel.add(declineButton);
        buttonPanel.add(acceptButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // Prevent closing without making a choice
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(
                    LicenseAcceptanceDialog.this,
                    "You must accept or decline the license agreement.\n" +
                    "If you decline, the application will close.\n\n" +
                    "Do you want to decline the license?",
                    "License Agreement Required",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    accepted = false;
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    /**
     * Shows the license acceptance dialog.
     * @return true if the user accepted the license, false otherwise
     */
    public boolean showDialog() {
        setVisible(true);
        return accepted;
    }

    /**
     * Checks if the license has been previously accepted.
     * @return true if the license was already accepted
     */
    public static boolean isLicenseAccepted() {
        Path acceptanceFile = AppConstants.getAppDataDirectory().resolve(ACCEPTANCE_FILE);
        return Files.exists(acceptanceFile);
    }

    /**
     * Saves the license acceptance flag to a file.
     */
    private void saveLicenseAcceptance() {
        try {
            Path acceptanceFile = AppConstants.getAppDataDirectory().resolve(ACCEPTANCE_FILE);
            String content = String.format(
                "License accepted on: %s\n" +
                "Software: %s\n" +
                "Version: %s\n",
                java.time.LocalDateTime.now(),
                AppConstants.SOFTWARE_NAME,
                AppConstants.VERSION
            );
            Files.writeString(acceptanceFile, content);
            System.out.println("License acceptance saved to: " + acceptanceFile);
        } catch (IOException e) {
            System.err.println("Warning: Could not save license acceptance: " + e.getMessage());
            // Not critical - user can still use the application
        }
    }

    /**
     * Shows the license acceptance dialog if needed (first run).
     * If the license is not accepted, the application exits.
     *
     * @param parent The parent frame for the dialog
     */
    public static void checkAndShowLicenseDialog(Frame parent) {
        if (!isLicenseAccepted()) {
            LicenseAcceptanceDialog dialog = new LicenseAcceptanceDialog(parent);
            boolean accepted = dialog.showDialog();
            if (!accepted) {
                System.exit(0);
            }
        }
    }
}
