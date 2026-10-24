# WareStat 1.3

WareStat 1.3 is the native desktop inventory and warehouse management release for Windows x64, macOS Apple Silicon and Intel, and Linux x64.

## Download and install

Choose the file matching your platform from the **Assets** section below:

| System | File |
| --- | --- |
| Windows x64 | `WareStat-1.3-windows-x64-setup.exe` |
| macOS Apple Silicon | `WareStat-1.3-macos-arm64.dmg` |
| macOS Intel | `WareStat-1.3-macos-x64.dmg` |
| Debian/Ubuntu Linux x64 | `WareStat-1.3-linux-x64.deb` |
| Portable Linux x64 | `WareStat-1.3-linux-x64.tar.gz` |

The two optional `-app.zip` files contain the macOS application bundles. Each package includes a `.sha256` checksum file.

The installer includes the application runtime; installing Java is not necessary. Linux needs system GTK/WebKitGTK dependencies.

## New in 1.3

- Signed-header WareStat `.wsbq` company database and backup format, with photo BLOB storage.
- Verified conversion of WareStat 1.2 `.db` installations while retaining the original.
- Customer/supplier operations, inventory tracking, invoices, PDF export and statistics.
- First-run licence acceptance and local consent receipt.
- Cross-platform packaging with installer and application-startup smoke tests.

## Upgrading safely

Back up existing data independently before upgrading. Existing WareStat 1.2 `data.db` files are never deleted by the automatic migration. WareStat 1.3 accepts genuine 1.2 `.db` backups and current `.wsbq` backups, but not `.wsbu`.

## Code-signing status

These packages are created by automated builds. **Windows Authenticode signing and Apple Developer ID notarization are not configured**. Windows may warn about an unknown publisher, and macOS Gatekeeper may block unsigned or unnotarized applications. This release does not claim Apple or Microsoft publisher verification.

See [the repository home](https://github.com/pizzomadone/management_software_2026) for installation guidance and technical documentation. The WareStat licence remains proprietary; free download does not grant redistribution rights.
