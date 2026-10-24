# WareStat

**Free, offline-first inventory and warehouse management for Windows, macOS and Linux.**

[![Native platform builds](https://github.com/pizzomadone/management_software_2026/actions/workflows/native-release.yml/badge.svg)](https://github.com/pizzomadone/management_software_2026/actions/workflows/native-release.yml)
[![Download releases](https://img.shields.io/badge/Downloads-GitHub_Releases-0969da)](https://github.com/pizzomadone/management_software_2026/releases)
[![Website](https://img.shields.io/badge/Website-warestat.com-555)](https://www.warestat.com/)

WareStat is a native desktop application for businesses that need to manage products, stock, customers, suppliers, sales and documents in one place. It stores company data locally; the optional Discover panel connects to the WareStat website.

## Download WareStat

**[Download the latest verified release for your operating system](https://github.com/pizzomadone/management_software_2026/releases/latest)**

The download files appear on the Releases page **only after Windows, both macOS architectures and Linux pass their packaging and startup tests**. Do not install files obtained from a failed or incomplete GitHub Actions run.

| Platform | Choose this release file | Installation |
| --- | --- | --- |
| Windows 10/11, x64 | `WareStat-<version>-windows-x64-setup.exe` | Run the installer; launch WareStat from the Start menu |
| macOS, Apple Silicon (M-series) | `WareStat-<version>-macos-arm64.dmg` | Open the DMG and drag WareStat into Applications |
| macOS, Intel | `WareStat-<version>-macos-x64.dmg` | Open the DMG and drag WareStat into Applications |
| Debian/Ubuntu-compatible Linux, x64 | `WareStat-<version>-linux-x64.deb` | Install with your graphical package installer or `sudo apt install ./WareStat-*.deb` |
| Linux, x64 portable | `WareStat-<version>-linux-x64.tar.gz` | Extract and execute `WareStat/warestat` |

**No Java installation is required.** Official packages contain compiled native WareStat code and required application libraries. Linux still needs the system GTK/WebKitGTK dependencies listed in its `.deb` package; the portable archive does not bundle them.

The macOS downloads also include a zipped `.app` for those who specifically need an app bundle. For normal installation, use the DMG.

### Operating-system security notices

The current CI uses **ad-hoc macOS signing**, not Apple Developer ID signing/notarization. macOS Gatekeeper may refuse to open the app until it is properly signed and notarized. The Windows installer is not currently Authenticode-signed; Windows SmartScreen may show an unrecognized-publisher warning. These are distribution-signing limitations, not evidence that the package passed an OS vendor's verification. Download only from this repository's Releases page and verify the accompanying `.sha256` file if desired.

### Verify a download

Every release includes one `.sha256` file per package. Compare the SHA-256 hash with the downloaded file:

- Windows PowerShell: `Get-FileHash .\WareStat-<version>-windows-x64-setup.exe -Algorithm SHA256`
- macOS: `shasum -a 256 WareStat-<version>-macos-arm64.dmg` (or `macos-x64`)
- Linux: `sha256sum WareStat-<version>-linux-x64.deb`

## What WareStat does

- **Stock and products:** product catalogue, barcode lookup, stock movements, inventory quantities and minimum-stock notifications.
- **Purchasing:** suppliers, purchasing documents and supplier orders, including returns and FIFO lot tracking.
- **Sales:** customer records, customer orders, invoices, payment tracking and PDF exports.
- **Reporting:** sales, inventory and warehouse statistics, with configurable currency.
- **Data protection:** locally stored company databases, backup/export/restore and migration of verified legacy WareStat 1.2 data.
- **Languages:** Italian, Spanish, Brazilian Portuguese, French, German and Polish.

## First launch and existing data

1. Launch WareStat, choose your interface language and review the end-user licence. Both consent confirmations are required to continue.
2. On a new installation, create a company database using **File → New Database**.
3. On an existing installation, retain an independent backup of your company data before upgrading.

WareStat 1.3 uses **`.wsbq`** company databases and backup files. A genuine WareStat 1.2 `data.db` installation can be migrated after confirmation, preserving the original `.db` file. Official 1.2 `.db` backups can also be imported. The unsupported `.wsbu` extension is **not** a WareStat 1.2 format.

The application is designed for local use. Do not have multiple PCs edit the same `.wsbq` file over a network share.

## Download and release status

A GitHub **Release** is the permanent public download page. A GitHub **Actions artifact** is a temporary internal build output and should not be used as the official download link. The [native build workflow](https://github.com/pizzomadone/management_software_2026/actions/workflows/native-release.yml) runs automated cross-platform checks; version-tagged releases are published only after those checks pass.

For the website's download page, link to [GitHub Releases (latest)](https://github.com/pizzomadone/management_software_2026/releases/latest) so the link remains current across versions.

## Documentation and development

- [Native installers and packaging](NATIVE_BUILD.md)
- [Build and development guide](BUILD.md)
- [Technical details and data format](docs/TECHNICAL_DETAILS.md)
- [Third-party licences](LICENSE-THIRD-PARTY.txt) and [notices](NOTICE.txt)

WareStat is proprietary software, provided free of charge under its end-user licence. **Free to use does not mean open-source or permission to redistribute.** See the in-app licence for the complete terms.

**Website:** [warestat.com](https://www.warestat.com/) · **Product news:** [Discover](https://www.warestat.com/discover/)
