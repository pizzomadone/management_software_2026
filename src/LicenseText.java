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
        "1. DEFINITIONS AND ACCEPTANCE\n" +
        "-----------------------------\n" +
        "\"Software\" means WareStat version %s, its executable code, documentation,\n" +
        "interfaces and components supplied under this Agreement.\n" +
        "\"Licensor\" means the owner of the rights in the Software distributing it under\n" +
        "the WareStat name. \"User\" means the person or organization using the Software.\n" +
        "By accepting this Agreement and then installing or using the Software, the User\n" +
        "accepts its terms. A person accepting for an organization represents that they\n" +
        "are authorized to do so. If the User does not accept, the Software must not be\n" +
        "used. This Agreement grants no rights beyond those expressly stated.\n" +
        "\n" +
        "2. FREE LICENSE GRANT AND PERMITTED USE\n" +
        "----------------------------------------\n" +
        "Subject to compliance with this Agreement, the Licensor grants the User a\n" +
        "non-exclusive, non-transferable, royalty-free license, terminable for a material\n" +
        "breach as set out in Section 16, to\n" +
        "run the Software for personal or commercial purposes, including managing an\n" +
        "organization's inventory and business operations. There is no per-device fee\n" +
        "and no numerical limit on installations on devices owned or controlled by the\n" +
        "User. Employees and authorized contractors may operate the Software on the\n" +
        "User's behalf. Authorized technicians may install or configure WareStat solely\n" +
        "for the User; this does not grant them independent distribution rights. Public\n" +
        "redistribution, sale, sublicensing or unrelated third-party copying is forbidden.\n" +
        "\n" +
        "3. RESTRICTIONS AND REDISTRIBUTION\n" +
        "----------------------------------\n" +
        "Except where expressly permitted by the Licensor or by non-waivable law, the\n" +
        "User must not modify, translate, adapt or create derivative versions of the\n" +
        "Software; reverse engineer, decompile or disassemble it; remove proprietary\n" +
        "notices; sell, rent, sublicense, publish, repackage or redistribute copies;\n" +
        "or use it unlawfully. Any distribution must be authorized by WareStat.\n" +
        "Nothing here restricts statutory rights to make permitted backup copies, study\n" +
        "lawfully accessible software, or achieve interoperability where applicable law\n" +
        "does not allow those rights to be waived.\n" +
        "\n" +
        "4. INTELLECTUAL PROPERTY AND USER DATA\n" +
        "---------------------------------------\n" +
        "The Software is licensed, not sold. The Licensor and any applicable third-party\n" +
        "rightsholders retain their respective intellectual property rights. No rights\n" +
        "in the Software are transferred to the User. The User retains any rights it\n" +
        "lawfully holds in business records, files and other data entered into or\n" +
        "created with the Software. The Licensor does not acquire ownership of those\n" +
        "records merely because WareStat processes them.\n" +
        "\n" +
        "5. NO WARRANTIES\n" +
        "----------------\n" +
        "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE SOFTWARE IS PROVIDED\n" +
        "FREE OF CHARGE, \"AS IS\" AND \"AS AVAILABLE\", WITHOUT ANY WARRANTY THAT\n" +
        "CAN LAWFULLY BE DISCLAIMED, EXPRESS OR IMPLIED, INCLUDING WARRANTIES OF\n" +
        "ACCURACY, RELIABILITY,\n" +
        "MERCHANTABILITY, SATISFACTORY QUALITY, FITNESS FOR A PARTICULAR PURPOSE,\n" +
        "NON-INFRINGEMENT, CONTINUOUS OPERATION OR FREEDOM FROM DEFECTS.\n" +
        "\n" +
        "THE LICENSOR DOES NOT WARRANT THAT FUNCTIONS, FORMULAS, REPORTS, OUTPUTS,\n" +
        "DATABASES, BACKUPS, IMPORTS, EXPORTS OR UPDATES WILL BE ERROR-FREE, COMPLETE,\n" +
        "SECURE, COMPATIBLE, AVAILABLE OR RECOVERABLE. RESULTS MAY BE INCORRECT,\n" +
        "OUTDATED, INCONSISTENT OR AFFECTED BY SETTINGS, INPUTS, ROUNDING, PLATFORM\n" +
        "DIFFERENCES, SOFTWARE DEFECTS OR USER ACTIONS. NO INFORMATION OR ADVICE\n" +
        "CREATES A WARRANTY UNLESS THE LICENSOR EXPRESSLY ASSUMES ONE IN WRITING.\n" +
        "MANDATORY STATUTORY WARRANTIES AND RIGHTS REMAIN UNAFFECTED.\n" +
        "\n" +
        "6. EXCLUSION OF ALL LIABILITY\n" +
        "------------------------------\n" +
        "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, IN NO EVENT SHALL THE\n" +
        "LICENSOR, SOFTWARE AUTHORS, DEVELOPERS OR AUTHORIZED DISTRIBUTORS BE LIABLE\n" +
        "FOR ANY DAMAGES, LOSSES, CLAIMS, COSTS OR LIABILITIES OF ANY KIND, WHETHER\n" +
        "DIRECT, INDIRECT, INCIDENTAL, CONSEQUENTIAL, SPECIAL, EXEMPLARY OR PUNITIVE,\n" +
        "ARISING OUT OF OR RELATED TO THE SOFTWARE, ITS USE, INABILITY TO USE,\n" +
        "FUNCTIONS, ERRORS, OMISSIONS, DEFECTS, SECURITY INCIDENTS, MALFUNCTIONS,\n" +
        "INTERRUPTIONS, OUTPUTS, DOCUMENTS OR DATA, EVEN IF ADVISED OF THE\n" +
        "POSSIBILITY OF SUCH DAMAGES.\n" +
        "\n" +
        "THIS EXCLUSION INCLUDES, WITHOUT LIMITATION, INCORRECT INVENTORY QUANTITIES,\n" +
        "STOCK AVAILABILITY, LOT OR FIFO VALUATIONS, PRICES, COSTS, MARGINS, PROFITS,\n" +
        "TAXES, VAT, INVOICES, ORDERS, RESERVATIONS, REPORTS, ANALYSES, FORECASTS,\n" +
        "IMPORTS, EXPORTS OR MIGRATIONS; DATA LOSS, ALTERATION OR CORRUPTION; BUSINESS\n" +
        "INTERRUPTION; LOST SALES, PROFITS, REVENUE, CUSTOMERS, CONTRACTS OR\n" +
        "OPPORTUNITIES; PENALTIES; AND CLAIMS BY CUSTOMERS, SUPPLIERS OR OTHER THIRD\n" +
        "PARTIES. THE EXCLUSION IS INTENDED TO APPLY REGARDLESS OF WHETHER A CLAIM\n" +
        "IS ASSERTED IN CONTRACT, TORT (INCLUDING NEGLIGENCE), STATUTE OR OTHERWISE,\n" +
        "TO THE EXTENT SUCH EXCLUSION IS LEGALLY PERMITTED.\n" +
        "\n" +
        "7. FALLBACK AGGREGATE LIABILITY CAP\n" +
        "------------------------------------\n" +
        "IF AND ONLY TO THE EXTENT A CLAIM CANNOT VALIDLY BE EXCLUDED UNDER SECTION\n" +
        "6 BUT THE AMOUNT OF LIABILITY MAY LAWFULLY BE LIMITED, THE TOTAL AGGREGATE\n" +
        "LIABILITY OF THE LICENSOR ARISING FROM OR RELATED TO THIS SOFTWARE AND\n" +
        "AGREEMENT SHALL NOT EXCEED EUR 5 (FIVE EUROS) IN THE AGGREGATE FOR EACH\n" +
        "USER, ACROSS ALL CLAIMS, INCIDENTS, TRANSACTIONS AND YEARS, REGARDLESS\n" +
        "OF THE NUMBER OR LEGAL BASIS OF CLAIMS. THIS IS NOT A PAYMENT PER CLAIM,\n" +
        "NOR AN ACKNOWLEDGMENT THAT ANY COMPENSATION IS OWED. SECTIONS 6 AND 7\n" +
        "ARE SEPARATE LIMITATIONS AND\n" +
        "SHALL EACH OPERATE TO THE FULLEST EXTENT LEGALLY PERMITTED.\n" +
        "\n" +
        "NOTHING IN THIS AGREEMENT EXCLUDES OR LIMITS LIABILITY OR REMEDIES WHEN\n" +
        "PROHIBITED BY MANDATORY LAW, INCLUDING FOR INTENTIONAL MISCONDUCT OR GROSS\n" +
        "NEGLIGENCE WHERE APPLICABLE, OR ANY OTHER NON-WAIVABLE RIGHTS.\n" +
        "\n" +
        "8. USER VERIFICATION AND RESPONSIBILITY\n" +
        "----------------------------------------\n" +
        "The Software is an information management tool, not a certification of the\n" +
        "accuracy or legal validity of its results. Before using the Software for\n" +
        "material business operations, the User is responsible for testing it in its\n" +
        "own environment with representative sample data, including relevant stock,\n" +
        "FIFO, invoice, tax and backup/restore operations. Before relying on outputs\n" +
        "for business, legal, tax, accounting, purchasing, selling or financial\n" +
        "decisions, the User must independently verify material quantities, amounts,\n" +
        "records and documents, with professional review where appropriate. The User\n" +
        "is responsible for its inputs, settings, tax rates and operational checks.\n" +
        "No provision removes responsibilities imposed by mandatory law.\n" +
        "\n" +
        "9. BUSINESS DECISIONS AND FINANCIAL RISKS\n" +
        "-----------------------------------------\n" +
        "WareStat does not provide accounting, tax, legal, auditing, investment or\n" +
        "business consultancy and does not guarantee regulatory compliance, stock\n" +
        "availability, document validity, profit or any commercial outcome. Decisions\n" +
        "and submissions remain the User's responsibility, subject to mandatory law.\n" +
        "\n" +
        "10. BACKUPS, SECURITY AND DATA RECOVERY\n" +
        "----------------------------------------\n" +
        "The User is responsible for maintaining separate, appropriately protected\n" +
        "copies of its data, independent from the active database, and for regularly\n" +
        "checking backup integrity and testing actual restoration. Independent backup\n" +
        "copies should be created before upgrades, migrations and irreversible\n" +
        "operations. A built-in backup feature does not replace these checks. Hardware\n" +
        "failure, operating-system issues, file damage, user actions and Software\n" +
        "defects may cause data loss. The Licensor makes no voluntary guarantee of\n" +
        "retention, restoration, confidentiality or uninterrupted access, subject to\n" +
        "mandatory law.\n" +
        "\n" +
        "11. IMPORTS, EXPORTS AND MIGRATIONS\n" +
        "------------------------------------\n" +
        "The User should preserve separate copies of source files and verify migrated,\n" +
        "imported and exported data before relying on them. Conversion between Software\n" +
        "versions or data formats can expose missing, incompatible, pre-existing\n" +
        "corrupt or inaccurately mapped data. Availability of a migration or recovery\n" +
        "feature does not guarantee success or compatibility with every source file.\n" +
        "The Licensor does not voluntarily warrant any conversion or restoration\n" +
        "result, except as required by applicable law.\n" +
        "\n" +
        "12. DATA PROTECTION AND NETWORK FEATURES\n" +
        "-----------------------------------------\n" +
        "WareStat stores company records in local database files. Opening the embedded\n" +
        "Discover page connects to the WareStat website at %s and necessarily transmits\n" +
        "connection information such as IP address and technical browser details;\n" +
        "website resources may initiate additional third-party requests. Merely opening\n" +
        "Discover does not upload the local company database. The User is responsible\n" +
        "for complying with data-protection requirements applicable to the personal\n" +
        "data it determines how and why to process, including selecting an appropriate\n" +
        "lawful basis where required. Nothing here excludes the Licensor's own\n" +
        "non-waivable data-protection obligations, if any.\n" +
        "\n" +
        "13. THIRD-PARTY SOFTWARE\n" +
        "-------------------------\n" +
        "The Software includes third-party components governed by their respective\n" +
        "licenses. See LICENSE-THIRD-PARTY.txt for the applicable notices and terms.\n" +
        "Nothing in this Agreement overrides rights granted or obligations imposed by\n" +
        "those separate licenses.\n" +
        "\n" +
        "14. NO SUPPORT, MAINTENANCE OR UPDATE COMMITMENT\n" +
        "------------------------------------------------\n" +
        "The free license does not include any voluntary commitment to provide\n" +
        "technical support, training, customizations, service levels, maintenance,\n" +
        "corrections, security fixes, continued availability or future updates. The\n" +
        "Licensor may release, modify or discontinue versions or features. Any\n" +
        "obligations imposed by mandatory law remain unaffected.\n" +
        "\n" +
        "15. UNLAWFUL USE AND THIRD-PARTY CLAIMS\n" +
        "----------------------------------------\n" +
        "The User must not distribute the Software without authorization or use it\n" +
        "to infringe laws or the rights of others. To the extent permitted by law,\n" +
        "the User shall indemnify the Licensor for substantiated third-party claims\n" +
        "and reasonable, documented defense costs directly caused by the User's\n" +
        "unauthorized distribution, intentional unlawful misuse, or unlawful data\n" +
        "or content supplied by the User. The Licensor shall, where reasonably\n" +
        "practicable, notify the User of such claims and allow reasonable participation\n" +
        "in their defense. No settlement imposing obligations on the User may be\n" +
        "entered into without the User's prior agreement, unless law requires it.\n" +
        "This indemnity does not apply to claims caused by defects in the Software\n" +
        "or by the Licensor's own acts or omissions, and does not remove any\n" +
        "mandatory consumer or other non-waivable rights.\n" +
        "\n" +
        "16. TERMINATION\n" +
        "---------------\n" +
        "The license remains effective until terminated. A material breach may result\n" +
        "in termination of the right to use the Software, subject to any notice or cure\n" +
        "requirements imposed by law. Upon termination the User must stop using and\n" +
        "delete unauthorized copies of the Software, without prejudice to rights or\n" +
        "obligations to retain its own business records, lawful backups or documents.\n" +
        "Clauses which by their nature must survive termination remain effective to\n" +
        "the extent legally permitted.\n" +
        "\n" +
        "17. FUTURE VERSIONS\n" +
        "--------------------\n" +
        "This Agreement applies to WareStat version %s. Future releases may be subject\n" +
        "to different terms, may be offered commercially, or may not be released at all.\n" +
        "The User has no contractual entitlement to a new version or continued free\n" +
        "upgrades. Lawful use of an earlier version under its accepted terms is not\n" +
        "automatically revoked solely because later versions use different terms.\n" +
        "\n" +
        "18. GOVERNING LAW AND COURTS\n" +
        "-----------------------------\n" +
        "To the extent permitted by applicable law, this Agreement is governed by the\n" +
        "laws of %s. Where a contractual choice of court is legally effective, the\n" +
        "competent courts shall include the %s. Any mandatory consumer forum,\n" +
        "applicable conflict-of-laws rule or other non-waivable jurisdictional\n" +
        "protection takes precedence.\n" +
        "\n" +
        "19. ENTIRE AGREEMENT, SEVERABILITY AND INTERPRETATION\n" +
        "-----------------------------------------------------\n" +
        "This Agreement states the license conditions for this Software version,\n" +
        "together with separate mandatory third-party license terms. If any provision\n" +
        "or limitation is invalid or unenforceable, the remainder shall remain effective\n" +
        "insofar as legally possible. Each exclusion, limitation and fallback in this\n" +
        "Agreement is intended to operate independently and to the maximum extent\n" +
        "permitted by law. Headings assist reading and do not narrow any provision.\n" +
        "Failure or delay by the Licensor to exercise a contractual right does not\n" +
        "by itself waive that right. Any waiver applies only to its stated occasion,\n" +
        "subject to applicable mandatory law. Nothing here displaces mandatory\n" +
        "statutory rights.\n" +
        "\n" +
        "20. CONTACT\n" +
        "-----------\n" +
        "Licensor: %s\n" +
        "Website: %s\n" +
        "Email: %s\n" +
        "\n" +
        "================================================================================\n" +
        "END OF LICENSE AGREEMENT\n" +
        "================================================================================\n",
            AppConstants.COPYRIGHT_YEAR,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.WEBSITE,
            AppConstants.LICENSE_VERSION_APPLIES_TO,
            AppConstants.WEBSITE,
            AppConstants.LICENSE_VERSION_APPLIES_TO,
            AppConstants.JURISDICTION_COUNTRY,
            AppConstants.JURISDICTION_COURT,
            AppConstants.COPYRIGHT_HOLDER,
            AppConstants.WEBSITE,
            AppConstants.CONTACT_EMAIL
        );
    }

    /**
     * Exact passages referenced by the separate approval checkbox.
     * Uses the same immutable text displayed in the acceptance dialog.
     */
    public static String getSpecificallyApprovedSections(String license) {
        java.util.regex.Pattern headings = java.util.regex.Pattern.compile(
            "(?m)^\\d+\\. [A-Z][^\\n]*$");
        StringBuilder selected = new StringBuilder();
        for (String number : EulaAcceptanceStore.SPECIFIC_CLAUSES.split(",")) {
            java.util.regex.Matcher matcher = headings.matcher(license);
            int start = -1;
            int end = license.length();
            while (matcher.find()) {
                if (start >= 0) {
                    end = matcher.start();
                    break;
                }
                if (matcher.group().startsWith(number + ". ")) {
                    start = matcher.start();
                }
            }
            if (start < 0) {
                throw new IllegalStateException("Missing license section " + number);
            }
            if (selected.length() > 0) selected.append("\n\n");
            selected.append(license.substring(start, end).strip());
        }
        return selected.toString();
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
