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

        // Website (clickable) - centered panel
        JPanel websitePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        websitePanel.setBackground(Color.WHITE);
        JLabel websiteLabel = new JLabel("<html><a href=''>www.warestat.com</a></html>");
        websiteLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        websiteLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        websiteLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openWebsite();
            }
        });
        websitePanel.add(websiteLabel);
        contentPanel.add(websitePanel);

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

        // Contact info (email as clickable link)
        JPanel contactPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        contactPanel.setBackground(Color.WHITE);
        JLabel contactTextLabel = new JLabel("Contact: ");
        contactTextLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        contactTextLabel.setForeground(Color.GRAY);
        JLabel emailLabel = new JLabel("<html><a href=''>" + AppConstants.CONTACT_EMAIL + "</a></html>");
        emailLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        emailLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        emailLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openEmail();
            }
        });
        contactPanel.add(contactTextLabel);
        contactPanel.add(emailLabel);
        contentPanel.add(contactPanel);

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

    private void openEmail() {
        try {
            Desktop.getDesktop().mail(new URI("mailto:" + AppConstants.CONTACT_EMAIL));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Could not open email client. Please send email to: " + AppConstants.CONTACT_EMAIL,
                "Information",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openLicenseFile() {
        showTextDialog("Software License", LicenseText.getSoftwareLicense());
    }

    private void openThirdPartyLicenses() {
        showTextFileDialog("LICENSE-THIRD-PARTY.txt", "Third-Party Licenses");
    }

    /**
     * Display text content in a dialog window.
     */
    private void showTextDialog(String title, String content) {
        // Create dialog
        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout());

        // Text area with scrolling
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(700, 500));
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Display a text file in a dialog window within the application.
     * Looks for the file in the executable directory first, then in working directory.
     */
    private void showTextFileDialog(String fileName, String title) {
        File file = null;

        try {
            // Try to find file in executable/JAR directory first
            String jarPath = AboutDialog.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File executableDir = jarFile.isDirectory() ? jarFile : jarFile.getParentFile();
            file = new File(executableDir, fileName);

            // If not found in executable dir, try working directory as fallback
            if (!file.exists()) {
                file = new File(fileName);
            }
        } catch (Exception e) {
            // If any error determining executable dir, use working directory
            file = new File(fileName);
        }

        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                fileName + " file not found in the application directory.",
                "File Not Found",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Read file content
            StringBuilder content = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Show using common method
            showTextDialog(title, content.toString());

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Error reading " + fileName + ":\n" + e.getMessage(),
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
