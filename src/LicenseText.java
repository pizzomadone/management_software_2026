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
"THIS SOFTWARE IS PROVIDED \"AS IS\" WITHOUT WARRANTY OF ANY KIND, EITHER\n" +
"EXPRESS OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF\n" +
"MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR NON-INFRINGEMENT.\n" +
"\n" +
"%s DOES NOT WARRANT THAT THE SOFTWARE WILL MEET YOUR REQUIREMENTS OR\n" +
"THAT THE OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR-FREE.\n" +
"\n" +
"================================================================================\n" +
"6. LIMITATION OF LIABILITY\n" +
"================================================================================\n" +
"\n" +
"IN NO EVENT SHALL %s BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,\n" +
"SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,\n" +
"PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;\n" +
"OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,\n" +
"WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR\n" +
"OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF\n" +
"ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.\n" +
"\n" +
"YOU ASSUME ALL RISKS AND RESPONSIBILITY FOR THE USE OF THIS SOFTWARE.\n" +
"\n" +
"================================================================================\n" +
"7. DATA AND PRIVACY\n" +
"================================================================================\n" +
"\n" +
"This software stores all data LOCALLY on your device. No data is transmitted\n" +
"to %s or any third party. You are the data controller for any\n" +
"personal data you process using this software.\n" +
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
"Any disputes arising from this agreement shall be subject to the exclusive\n" +
"jurisdiction of the %s.\n" +
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
     * Generate LICENSE.txt file if it doesn't exist.
     * Checks both executable directory and database directory before creating.
     * Should be called at application startup.
     */
    public static void ensureLicenseFileExists() {
        try {
            // Determine the directory where the executable/JAR is located
            String jarPath = LicenseText.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            java.io.File jarFile = new java.io.File(jarPath);
            java.io.File executableDir = jarFile.isDirectory() ? jarFile : jarFile.getParentFile();

            // Check if LICENSE.txt exists in executable directory
            java.io.File licenseInExeDir = new java.io.File(executableDir, "LICENSE.txt");

            // Check if LICENSE.txt exists in database directory
            java.io.File licenseInDataDir = new java.io.File(
                AppConstants.getAppDataDirectory().toFile(), "LICENSE.txt");

            // If exists in either location, don't create it
            if (licenseInExeDir.exists()) {
                System.out.println("LICENSE.txt found in executable directory, skipping creation.");
                return;
            }
            if (licenseInDataDir.exists()) {
                System.out.println("LICENSE.txt found in data directory, skipping creation.");
                return;
            }

            // Create in executable directory (doesn't exist in either location)
            java.io.FileWriter writer = new java.io.FileWriter(licenseInExeDir);
            writer.write(getSoftwareLicense());
            writer.close();
            System.out.println("LICENSE.txt generated successfully at: " + licenseInExeDir.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Warning: Could not create LICENSE.txt: " + e.getMessage());
            // Fallback: try to create in working directory
            try {
                java.io.File fallbackFile = new java.io.File("LICENSE.txt");
                if (!fallbackFile.exists()) {
                    java.io.FileWriter writer = new java.io.FileWriter(fallbackFile);
                    writer.write(getSoftwareLicense());
                    writer.close();
                    System.out.println("LICENSE.txt generated in working directory (fallback).");
                }
            } catch (java.io.IOException ex) {
                System.err.println("Warning: Could not create LICENSE.txt even in fallback location: " + ex.getMessage());
            }
        }
    }
}
