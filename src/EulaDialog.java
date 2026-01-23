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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Dialog to display and request acceptance of the End User License Agreement (EULA).
 * This dialog is shown on first application startup and must be accepted to continue.
 */
public class EulaDialog extends JDialog {
    private boolean accepted = false;

    public EulaDialog(Frame parent) {
        super(parent, "End User License Agreement", true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(new Color(240, 240, 240));
        titlePanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel titleLabel = new JLabel("End User License Agreement");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);

        // License text area
        JTextArea licenseTextArea = new JTextArea(LicenseText.getSoftwareLicense());
        licenseTextArea.setEditable(false);
        licenseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        licenseTextArea.setLineWrap(true);
        licenseTextArea.setWrapStyleWord(true);
        licenseTextArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(licenseTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with checkbox and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 15, 20));

        // Checkbox panel
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox acceptCheckBox = new JCheckBox("I have read and accept the terms of this license agreement");
        acceptCheckBox.setFont(new Font("Arial", Font.PLAIN, 12));
        checkboxPanel.add(acceptCheckBox);
        bottomPanel.add(checkboxPanel, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton acceptButton = new JButton("Accept");
        acceptButton.setFont(new Font("Arial", Font.BOLD, 12));
        acceptButton.setEnabled(false); // Initially disabled
        acceptButton.setPreferredSize(new Dimension(100, 30));
        acceptButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                accepted = true;
                AppConstants.markEulaAsAccepted();
                dispose();
            }
        });

        JButton declineButton = new JButton("Decline");
        declineButton.setFont(new Font("Arial", Font.PLAIN, 12));
        declineButton.setPreferredSize(new Dimension(100, 30));
        declineButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int result = JOptionPane.showConfirmDialog(
                    EulaDialog.this,
                    "You must accept the license agreement to use this software.\n" +
                    "Do you want to exit the application?",
                    "License Not Accepted",
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

        // Enable Accept button only when checkbox is checked
        acceptCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptButton.setEnabled(acceptCheckBox.isSelected());
            }
        });

        buttonsPanel.add(declineButton);
        buttonsPanel.add(acceptButton);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Checks if the user accepted the EULA.
     *
     * @return true if accepted, false otherwise
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Shows the EULA dialog and returns whether it was accepted.
     *
     * @param parent The parent frame
     * @return true if accepted, false otherwise
     */
    public static boolean showDialog(Frame parent) {
        EulaDialog dialog = new EulaDialog(parent);
        dialog.setVisible(true);
        return dialog.isAccepted();
    }
}
