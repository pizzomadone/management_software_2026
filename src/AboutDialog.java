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
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * About dialog displaying software information, copyright, and license details.
 */
public class AboutDialog extends JDialog {

    public AboutDialog(Frame parent) {
        super(parent, "About " + AppConstants.SOFTWARE_NAME, true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        contentPanel.setBackground(Color.WHITE);

        // Software name - large and bold
        JLabel nameLabel = new JLabel(AppConstants.SOFTWARE_NAME);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(nameLabel);

        contentPanel.add(Box.createVerticalStrut(5));

        // Version
        JLabel versionLabel = new JLabel("Version " + AppConstants.VERSION);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setForeground(Color.GRAY);
        contentPanel.add(versionLabel);

        contentPanel.add(Box.createVerticalStrut(20));

        // Copyright
        JLabel copyrightLabel = new JLabel(AppConstants.COPYRIGHT_FULL);
        copyrightLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(copyrightLabel);

        contentPanel.add(Box.createVerticalStrut(5));

        // Website (clickable)
        JLabel websiteLabel = new JLabel("<html><a href=''>www.warestat.com</a></html>");
        websiteLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        websiteLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        websiteLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        websiteLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openWebsite();
            }
        });
        contentPanel.add(websiteLabel);

        contentPanel.add(Box.createVerticalStrut(20));

        // License type
        JLabel licenseTypeLabel = new JLabel(AppConstants.LICENSE_TYPE + " - Version " + AppConstants.LICENSE_VERSION_APPLIES_TO);
        licenseTypeLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        licenseTypeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(licenseTypeLabel);

        contentPanel.add(Box.createVerticalStrut(10));

        // License summary in text area (read-only, wrapped)
        JTextArea licenseSummary = new JTextArea(AppConstants.LICENSE_SUMMARY);
        licenseSummary.setEditable(false);
        licenseSummary.setLineWrap(true);
        licenseSummary.setWrapStyleWord(true);
        licenseSummary.setFont(new Font("SansSerif", Font.PLAIN, 10));
        licenseSummary.setBackground(contentPanel.getBackground());
        licenseSummary.setForeground(Color.DARK_GRAY);
        licenseSummary.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        licenseSummary.setAlignmentX(Component.CENTER_ALIGNMENT);
        licenseSummary.setMaximumSize(new Dimension(400, 50));
        contentPanel.add(licenseSummary);

        contentPanel.add(Box.createVerticalStrut(20));

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.setBackground(Color.WHITE);

        JButton viewLicenseButton = new JButton("View License");
        viewLicenseButton.addActionListener(e -> openLicenseFile());
        buttonsPanel.add(viewLicenseButton);

        JButton thirdPartyButton = new JButton("Third-Party Licenses");
        thirdPartyButton.addActionListener(e -> openThirdPartyLicenses());
        buttonsPanel.add(thirdPartyButton);

        contentPanel.add(buttonsPanel);

        contentPanel.add(Box.createVerticalStrut(15));

        // Contact info
        JLabel contactLabel = new JLabel("Contact: " + AppConstants.CONTACT_EMAIL);
        contactLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        contactLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contactLabel.setForeground(Color.GRAY);
        contentPanel.add(contactLabel);

        add(contentPanel, BorderLayout.CENTER);

        // Close button at bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(Color.WHITE);
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Set dialog properties
        pack();
        setLocationRelativeTo(getParent());
    }

    private void openWebsite() {
        try {
            Desktop.getDesktop().browse(new URI("https://" + AppConstants.WEBSITE));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Could not open browser. Please visit: " + AppConstants.WEBSITE,
                "Information",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openLicenseFile() {
        File licenseFile = new File("LICENSE.txt");
        if (!licenseFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "LICENSE.txt file not found in the application directory.",
                "File Not Found",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(licenseFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Could not open LICENSE.txt file.\nPlease open it manually from the application folder.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openThirdPartyLicenses() {
        File thirdPartyFile = new File("LICENSE-THIRD-PARTY.txt");
        if (!thirdPartyFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "LICENSE-THIRD-PARTY.txt file not found in the application directory.",
                "File Not Found",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(thirdPartyFile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Could not open LICENSE-THIRD-PARTY.txt file.\nPlease open it manually from the application folder.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Show the About dialog.
     * @param parent the parent frame
     */
    public static void showDialog(Frame parent) {
        AboutDialog dialog = new AboutDialog(parent);
        dialog.setVisible(true);
    }
}
