/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * See LICENSE.txt for full terms.
 */

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/** Modal, mandatory EULA acceptance before WareStat initializes any company database. */
public class EulaDialog extends JDialog {
    private boolean accepted = false;

    public EulaDialog(Frame parent) {
        super(parent, "End User License Agreement", true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);
        Dialogs.centerWindow(this);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(new Color(240, 240, 240));
        titlePanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel titleLabel = new JLabel("End User License Agreement");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);

        // Retain the exact string displayed: the acceptance record hashes these bytes.
        final String displayedLicenseText = LicenseText.getSoftwareLicense();
        JTextArea licenseTextArea = new JTextArea(displayedLicenseText);
        licenseTextArea.setEditable(false);
        licenseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        licenseTextArea.setLineWrap(true);
        licenseTextArea.setWrapStyleWord(true);
        licenseTextArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(licenseTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 15, 20));

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        JCheckBox generalCheckBox = new JCheckBox(
            "I have read and accept the terms of this license agreement.");
        generalCheckBox.setFont(new Font("Arial", Font.PLAIN, 12));
        generalCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox specificCheckBox = new JCheckBox(
            "<html>I specifically approve Sections 5 (No Warranties), 6 (Exclusion of Liability),<br>" +
            "7 (EUR 5 Liability Cap), 15 (Third-Party Claims), 16 (Termination),<br>" +
            "and 18 (Governing Law / Courts).</html>");
        specificCheckBox.setFont(new Font("Arial", Font.PLAIN, 12));
        specificCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkboxPanel.add(generalCheckBox);
        checkboxPanel.add(Box.createVerticalStrut(5));
        checkboxPanel.add(specificCheckBox);
        checkboxPanel.add(Box.createVerticalStrut(5));
        JButton reviewClausesButton = new JButton("Read the specifically approved sections");
        reviewClausesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        reviewClausesButton.addActionListener(event -> {
            JTextArea clausesArea = new JTextArea(
                LicenseText.getSpecificallyApprovedSections(displayedLicenseText));
            clausesArea.setEditable(false);
            clausesArea.setLineWrap(true);
            clausesArea.setWrapStyleWord(true);
            clausesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            clausesArea.setCaretPosition(0);
            JScrollPane clausesScrollPane = new JScrollPane(clausesArea);
            clausesScrollPane.setPreferredSize(new Dimension(680, 360));
            JOptionPane.showMessageDialog(
                EulaDialog.this,
                clausesScrollPane,
                "Sections requiring separate approval",
                JOptionPane.INFORMATION_MESSAGE);
        });
        checkboxPanel.add(reviewClausesButton);
        bottomPanel.add(checkboxPanel, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton declineButton = new JButton("Decline");
        declineButton.setPreferredSize(new Dimension(100, 30));
        declineButton.addActionListener(event -> {
            int result = Dialogs.showConfirmDialog(
                EulaDialog.this,
                "You must accept the license agreement to use this software.\n" +
                "Do you want to exit the application?",
                "License Not Accepted",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                accepted = false;
                dispose();
            }
        });

        JButton acceptButton = new JButton("Accept");
        acceptButton.setFont(new Font("Arial", Font.BOLD, 12));
        acceptButton.setPreferredSize(new Dimension(100, 30));
        acceptButton.setEnabled(false);

        Runnable refreshAcceptance = () ->
            acceptButton.setEnabled(generalCheckBox.isSelected() && specificCheckBox.isSelected());
        generalCheckBox.addActionListener(event -> refreshAcceptance.run());
        specificCheckBox.addActionListener(event -> refreshAcceptance.run());

        acceptButton.addActionListener(event -> {
            try {
                EulaAcceptanceStore.recordAcceptance(
                    displayedLicenseText, generalCheckBox.isSelected(), specificCheckBox.isSelected());
                accepted = true;
                dispose();
            } catch (IOException error) {
                // Never claim acceptance or proceed with a failed/partial disk or preference write.
                Dialogs.showMessageDialog(EulaDialog.this,
                    "Unable to save the license acceptance.\n" +
                    "Check available disk space and folder permissions, then retry.\n\n" +
                    error.getMessage(),
                    "License Acceptance Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        buttonsPanel.add(declineButton);
        buttonsPanel.add(acceptButton);
        bottomPanel.add(buttonsPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public static boolean showDialog(Frame parent) {
        EulaDialog dialog = new EulaDialog(parent);
        dialog.setVisible(true);
        return dialog.isAccepted();
    }
}
