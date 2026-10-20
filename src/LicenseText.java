/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

/**
 * Contains the complete license text for the software.
 * This is the single source of truth for all license information.
 * License files are generated from this class at runtime if not present.
 */
public class LicenseText {

    /**
     * Generate the complete software license text.
     * Uses parameters from AppConstants for dynamic values.
     */
    public static String getSoftwareLicense() {
        return String.format(
"================================================================================\n" +
"WARESTAT SOFTWARE LICENSE AGREEMENT\n" +
"================================================================================\n" +
"\n" +
"Copyright © %s %s (%s)\n" +
"All Rights Reserved.\n" +
"\n" +
"================================================================================\n" +
"1. LICENSE GRANT\n" +
"================================================================================\n" +
"\n" +
"This version of %s (version %s) is provided FREE OF CHARGE for both\n" +
"PERSONAL and COMMERCIAL use, subject to the terms and conditions of this\n" +
"license agreement.\n" +
"\n" +
"By installing, copying, or using this software, you agree to be bound by the\n" +
"terms of this license.\n" +
"\n" +
"================================================================================\n" +
"2. PERMITTED USE\n" +
"================================================================================\n" +
"\n" +
"You are permitted to:\n" +
"\n" +
"  ✓ Use this software for personal purposes\n" +
"  ✓ Use this software for commercial purposes\n" +
"  ✓ Install this software on unlimited number of devices that you own or control\n" +
"  ✓ Use this software to manage your business operations\n" +
"\n" +
"================================================================================\n" +
"3. RESTRICTIONS\n" +
"================================================================================\n" +
"\n" +
"You are NOT permitted to:\n" +
"\n" +
"  ✗ Modify, adapt, translate, or create derivative works of this software\n" +
"  ✗ Reverse engineer, decompile, disassemble, or attempt to discover the\n" +
"    source code of this software\n" +
"  ✗ Remove, alter, or obscure any copyright, trademark, or other proprietary\n" +
"    rights notices contained in or on the software\n" +
"  ✗ Redistribute, sell, lease, sublicense, or otherwise transfer this software\n" +
"    to any third party, whether for free or for commercial gain\n" +
"  ✗ Use this software for any illegal or unauthorized purpose\n" +
"\n" +
"These restrictions do not limit rights that cannot be excluded under\n" +
"applicable law, including mandatory software interoperability exceptions.\n" +
"\n" +
"REDISTRIBUTION IS STRICTLY PROHIBITED. This software may only be distributed\n" +
"by %s or authorized parties. Any unauthorized distribution, whether free\n" +
"or commercial, constitutes a violation of this license and applicable copyright\n" +
"laws.\n" +
"\n" +
"================================================================================\n" +
"4. OWNERSHIP\n" +
"================================================================================\n" +
"\n" +
"This software is proprietary and is protected by copyright laws and\n" +
"international treaty provisions. %s (%s) retains all\n" +
"rights, title, and interest in and to the software, including all copyrights,\n" +
"patents, trade secrets, trademarks, and other intellectual property rights.\n" +
"\n" +
"Your license does NOT grant you any ownership rights to the software.\n" +
"\n" +
"================================================================================\n" +
"5. NO WARRANTY\n" +
"================================================================================\n" +
"\n" +
"TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THIS SOFTWARE IS\n" +
"PROVIDED \"AS IS\" WITHOUT VOLUNTARY WARRANTIES OF ANY KIND. MANDATORY\n" +
"STATUTORY RIGHTS AND WARRANTIES ARE NOT EXCLUDED OR RESTRICTED.\n" +
"\n" +
"%s CREATOR DOES NOT GUARANTEE UNINTERRUPTED OR ERROR-FREE OPERATION.\n" +
"INVENTORY, COST, PROFIT, TAX, VAT, INVOICE, REPORTING, AND OTHER BUSINESS\n" +
"CALCULATIONS MAY CONTAIN ERRORS OR REQUIRE USER-SPECIFIC CONFIGURATION.\n" +
"USERS MUST INDEPENDENTLY CHECK RESULTS BEFORE RELYING ON THEM FOR LEGAL,\n" +
"TAX, ACCOUNTING, COMMERCIAL, OR FINANCIAL DECISIONS. KEEP VERIFIED BACKUPS.\n" +
"\n" +
"================================================================================\n" +
"6. LIMITATION OF LIABILITY\n" +
"================================================================================\n" +
"\n" +
"TO THE EXTENT PERMITTED BY APPLICABLE LAW, THE %s CREATOR SHALL NOT\n" +
"BE LIABLE FOR LOSSES ARISING FROM RELIANCE ON INCORRECT CALCULATIONS,\n" +
"REPORTS, INVENTORY RECORDS, INVOICES, DATA LOSS, LOST PROFITS, OR BUSINESS\n" +
"INTERRUPTION. USERS REMAIN RESPONSIBLE FOR VERIFYING IMPORTANT OUTPUTS.\n" +
"\n" +
"NOTHING IN THIS AGREEMENT EXCLUDES OR LIMITS LIABILITY WHERE SUCH EXCLUSION\n" +
"OR LIMITATION IS PROHIBITED BY MANDATORY LAW, INCLUDING LIABILITY ARISING\n" +
"FROM INTENTIONAL MISCONDUCT OR GROSS NEGLIGENCE WHERE APPLICABLE.\n" +
"\n" +
"================================================================================\n" +
"7. DATA AND PRIVACY\n" +
"================================================================================\n" +
"\n" +
"WareStat stores company records in local database files. The embedded\n" +
"Discover page connects to %s's website and necessarily transmits\n" +
"connection information (such as your IP address and technical user-agent\n" +
"details including device, operating system, language, and display settings).\n" +
"Website resources may involve additional third-party network requests.\n" +
"The local company database is not uploaded merely by opening Discover.\n" +
"Where you determine the purposes and means of processing business personal\n" +
"data, you are responsible for complying with applicable privacy laws.\n" +
"\n" +
"You are responsible for:\n" +
"  • Compliance with applicable data protection laws (including GDPR)\n" +
"  • Obtaining necessary consents from data subjects\n" +
"  • Implementing appropriate security measures\n" +
"  • Managing data backup and recovery\n" +
"\n" +
"================================================================================\n" +
"8. TERMINATION\n" +
"================================================================================\n" +
"\n" +
"This license is effective until terminated. Your rights under this license\n" +
"will terminate automatically without notice if you fail to comply with any\n" +
"term of this agreement.\n" +
"\n" +
"Upon termination, you must:\n" +
"  • Cease all use of the software\n" +
"  • Destroy all copies of the software in your possession\n" +
"\n" +
"================================================================================\n" +
"9. FUTURE VERSIONS\n" +
"================================================================================\n" +
"\n" +
"This license agreement applies specifically to %s version %s.\n" +
"\n" +
"%s reserves the right to release future versions of this software under\n" +
"different license terms, including commercial licenses.\n" +
"\n" +
"Users of version %s may continue to use that version under these terms, but\n" +
"are not automatically entitled to upgrades or future versions.\n" +
"\n" +
"================================================================================\n" +
"10. THIRD-PARTY COMPONENTS\n" +
"================================================================================\n" +
"\n" +
"This software includes third-party open-source libraries, each with its own\n" +
"license terms. See LICENSE-THIRD-PARTY.txt for complete information.\n" +
"\n" +
"The licenses for third-party components are separate from this license and\n" +
"remain subject to their original terms.\n" +
"\n" +
"================================================================================\n" +
"11. GOVERNING LAW\n" +
"================================================================================\n" +
"\n" +
"This agreement shall be governed by and construed in accordance with the laws\n" +
"of %s.\n" +
"\n" +
"Subject to any mandatory consumer-protection rules and jurisdictional\n" +
"requirements, disputes shall be subject to the competent courts, including\n" +
"the %s where legally applicable. Mandatory consumer forums prevail.\n" +
"\n" +
"================================================================================\n" +
"12. ENTIRE AGREEMENT\n" +
"================================================================================\n" +
"\n" +
"This license agreement constitutes the entire agreement between you and\n" +
"%s concerning the software and supersedes all prior agreements and\n" +
"understandings.\n" +
"\n" +
"================================================================================\n" +
"13. SEVERABILITY\n" +
"================================================================================\n" +
"\n" +
"If any provision of this agreement is held to be invalid or unenforceable,\n" +
"the remaining provisions shall remain in full force and effect.\n" +
"\n" +
"================================================================================\n" +
"14. CONTACT INFORMATION\n" +
"================================================================================\n" +
"\n" +
"For questions regarding this license, please contact:\n" +
"\n" +
"  %s\n" +
"  Website: %s\n" +
"  Email: %s\n" +
"\n" +
"================================================================================\n" +
"END OF LICENSE AGREEMENT\n" +
"================================================================================\n",
            AppConstants.COPYRIGHT_YEAR,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.WEBSITE,
            AppConstants.SOFTWARE_NAME,
            AppConstants.LICENSE_VERSION_APPLIES_TO,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.WEBSITE,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.SOFTWARE_NAME,
            AppConstants.LICENSE_VERSION_APPLIES_TO,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.LICENSE_VERSION_APPLIES_TO,
            AppConstants.JURISDICTION_COUNTRY,
            AppConstants.JURISDICTION_COURT,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.WEBSITE,
            AppConstants.CONTACT_EMAIL
        );
    }

    /**
     * Synchronize the user-visible LICENSE.txt with the currently installed version.
     * Accepted copies and receipts are kept separately by EulaAcceptanceStore.
     */
    public static void ensureLicenseFileExists() {
        try {
            java.nio.file.Path file = AppConstants.getAppDataDirectory().resolve("LICENSE.txt");
            String current = getSoftwareLicense();
            if (java.nio.file.Files.isRegularFile(file)
                    && current.equals(java.nio.file.Files.readString(
                        file, java.nio.charset.StandardCharsets.UTF_8))) {
                return;
            }
            java.nio.file.Files.writeString(file, current, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("LICENSE.txt updated at: " + file);
        } catch (Exception e) {
            System.err.println("Warning: Could not update LICENSE.txt: " + e.getMessage());
        }
    }

    /**
     * Main method to display the license in a GUI window.
     * Used when running license.exe as a standalone application.
     */
    public static void main(String[] args) {
        // Set look and feel
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel if system LAF fails
        }

        // Create and show GUI on Event Dispatch Thread
        javax.swing.SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        // Create main frame
        javax.swing.JFrame frame = new javax.swing.JFrame(AppConstants.SOFTWARE_NAME + " - License Agreement");
        frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // Create text area for license
        javax.swing.JTextArea textArea = new javax.swing.JTextArea();
        textArea.setText(getSoftwareLicense());
        textArea.setEditable(false);
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        textArea.setMargin(new java.awt.Insets(10, 10, 10, 10));
        textArea.setCaretPosition(0); // Scroll to top

        // Add text area to scroll pane
        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Create button panel
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 10));

        // Add close button
        javax.swing.JButton closeButton = new javax.swing.JButton("Close");
        closeButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(closeButton);

        // Add copy button
        javax.swing.JButton copyButton = new javax.swing.JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
            textArea.setCaretPosition(0);
            Dialogs.showMessageDialog(frame,
                "License text copied to clipboard!",
                "Copied",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(copyButton);

        // Layout frame
        frame.setLayout(new java.awt.BorderLayout());
        frame.add(scrollPane, java.awt.BorderLayout.CENTER);
        frame.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        // Center frame on screen
        frame.setLocationRelativeTo(null);

        // Show frame
        frame.setVisible(true);
    }
}
