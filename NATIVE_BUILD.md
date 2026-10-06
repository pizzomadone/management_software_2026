# WareStat Native Image build and release guide

This document records the production Native Image pipeline that builds WareStat for Windows, macOS and Linux. It is intentionally kept in the repository so future releases do not depend on undocumented manual steps.

## Production strategy

The official Native Image path compiles WareStat ahead of time with Liberica Native Image Kit (NIK) Full for Java 21. The resulting application code is native machine code rather than a distributable JAR/class tree.

The legacy `build/release-hardening` branch uses a different strategy (ProGuard + jlink + jpackage). Keep that branch as a fallback/reference; do not mix its build pipeline into Native Image unless a specific independent signing/verification helper is deliberately ported.

## Supported release targets

GitHub Actions currently builds and smoke-tests:

- Windows x64: Inno Setup installer
- macOS Apple Silicon arm64: `.app` + `.dmg`
- macOS Intel x64: `.app` + `.dmg`
- Linux x64: Debian `.deb` + portable `.tar.gz`

Every customer-facing package is accompanied by a SHA-256 checksum.

## Source and Java version

- Application source: `src/`
- Build system: Apache Ant
- Java level: JDK 21
- Native compiler: BellSoft Liberica NIK Full 23.1.12.1 / Java 21.0.12.1
- Application version: `app.version` in `build.properties`

The workflow pins the NIK download hashes for each platform. Do not casually update NIK/JDK versions: update one platform at a time and require the complete smoke suite to pass.

## Files that are part of the Native Image build contract

Do not remove these as "experimental leftovers" unless the workflow is changed and fully revalidated:

- `.github/workflows/native-release.yml`
- `packaging/GraalProbe.java`
- `packaging/WareStatNativeLauncher.java`
- `packaging/graalvm-macos-build.sh`
- `packaging/graalvm-windows-reflect-config.json`
- `packaging/jawt_bridge.c`
- `packaging/native_launcher_windows.c`
- `packaging/native_launcher_windows.rc`
- `packaging/WareStatNative.iss`
- `packaging/native_launcher_macos.c`
- `packaging/native_launcher_linux.sh`

The probe classes are CI/build diagnostics. They are not customer-facing application source, but they are important because they exercise reflection/JNI/resources and the real Swing/WebView startup path before artifacts are accepted.

## Features explicitly validated by CI

The Native Image build has dedicated coverage for:

- Swing/AWT startup
- the real WareStat `MainWindow`
- embedded WebView
- SQLite JDBC/JNI
- PDFBox PDF generation
- Unicode/font handling
- ImageIO
- ResourceBundle/i18n for IT, ES, PT-BR, FR, DE and PL
- packaged/installed application startup

A green compiler step alone is not sufficient. The release is accepted only after the packaged application smoke tests pass.

## Important platform-specific discoveries

### Windows

Native Image + AWT proved sensitive to the generated executable identity/name. The working rule is:

- generate the Native Image executable directly with its intended native name;
- do not generate it under one name and later rename/copy it as a substitute for the tested executable.

The distribution uses a small native `WareStat.exe` launcher and keeps `WareStat-native.exe` plus required native libraries in the install directory. The installer is per-user so WebView2 can create its user-data directory without requiring Program Files write access.

Do not package CI-created WebView2 profile/runtime state.

### macOS

The application is packaged as a real `WareStat.app`, then into a DMG.

The native launcher resolves its own bundle path and makes the SQLite native library available before starting `WareStat-native`.

Intel runners required a fixed-size writable staging DMG followed by conversion to compressed UDZO. Do not revert to relying on `hdiutil -srcfolder` auto-sizing without retesting both Intel and Apple Silicon.

Current CI uses ad-hoc signing to validate bundle structure. Public commercial distribution still requires Developer ID signing, Hardened Runtime as appropriate, and Apple notarization.

### Linux

Linux requires GTK/WebKitGTK system packages for the embedded browser. The CI validates the real `MainWindow` + embedded WebView path under Xvfb.

The standalone offscreen WebView diagnostic is deliberately not the Linux release gate because GTK's offscreen widget path can time out even when the real embedded WebView works.

The real Linux MainWindow/WebView probe keeps JavaScript validation mandatory but allows a longer startup window under Xvfb; its watchdog must remain longer than the JavaScript readiness timeout, otherwise CI can kill a healthy WebKitGTK startup prematurely.

The DEB installs application files under `/opt/warestat` and exposes `/usr/bin/warestat`. The launcher must resolve `/opt/warestat` correctly even when invoked through that symlink. The portable tarball falls back to the directory containing the launcher.

## Native libraries

Native Image does not make every dependency a single monolithic file.

Most Java application/library code is compiled into the Native Image executable. Some platform-native components remain separate (for example SQLite/AWT/JNI-related native libraries). These files are expected and must remain beside/in the location required by the launcher.

Do not remove DLL/DYLIB/SO files merely because WareStat itself was compiled to machine code.

## Reachability metadata

Native Image needs metadata for code reached through reflection, JNI, resources and similar dynamic mechanisms.

The workflow collects/uses reachability metadata during real WareStat startup and includes explicit Windows reflection metadata where required.

When adding or upgrading a library that uses reflection, JNI, dynamic class loading, resource lookup or ServiceLoader, assume that Native Image metadata may need to change. A normal JVM build succeeding does not prove the Native Image build will succeed.

The Linux Native Image agent may periodically write metadata snapshots into temporary `agent-*.tmp*` directories. The workflow promotes the latest valid cumulative snapshot before native compilation. This behavior is intentional.

## Release artifacts

The workflow uploads GitHub Actions artifacts named:

- `WareStat-windows-x64`
- `WareStat-macos-arm64`
- `WareStat-macos-x64`
- `WareStat-linux-x64`

Artifacts are temporary CI outputs. For commercial/public releases, publish the final installers in a versioned GitHub Release (or the official WareStat download channel) rather than relying on expiring Actions artifacts.

## SHA-256 files

The `.sha256` files contain integrity hashes for the corresponding installer/archive. They are not needed to launch WareStat, but should be published with official downloads so a downloaded file can be verified against the CI-produced release.

## Future development workflow

Application development remains normal Java development:

1. change `src/` and related resources;
2. run normal tests locally;
3. push;
4. let the Native Image CI rebuild all supported targets;
5. do not release unless all platform jobs are green.

The Native Image instructions live in the repository, so future source changes do not require recreating the setup manually. New dynamic/JNI-heavy dependencies may require metadata changes; that is why the CI probes are retained.

## Commercial release checklist

Before public commercial distribution:

- keep all four Native Image packaging/smoke jobs green;
- sign Windows release executables/installer with an appropriate Authenticode certificate;
- sign/notarize macOS with Apple Developer ID credentials;
- verify SHA-256 files;
- test final installers on clean target machines;
- publish immutable versioned release artifacts;
- preserve third-party notices/licenses included by the packaging.

Signing/notarization is a release-authenticity layer. It is separate from Native Image compilation and must not replace the runtime smoke tests.

## Known-good milestone

The first fully green four-platform Native Image run was produced from branch `experiment/graalvm-native` after Linux packaging support was added. The working pipeline includes all platform-specific fixes described above.

When reorganizing branches or workflow filenames, preserve the content and behavior of this build contract and re-run all four targets before declaring the reorganization complete.
