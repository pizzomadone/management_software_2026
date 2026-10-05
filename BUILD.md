# WareStat build and release guide

WareStat uses Apache Ant and JDK 21. Development builds remain easy to debug; production builds use a separate hardened pipeline.

## Requirements

- JDK 21 with `jlink` and `jpackage`
- Apache Ant
- Windows installer builds: WiX Toolset 3
- Internet access on the first production build so the verified ProGuard archive can be downloaded

The application libraries are committed under `lib/`. ProGuard 7.10.0 is downloaded by `build.xml` and accepted only if its SHA-256 checksum matches the pinned value.

## Common commands

```bash
ant clean test
ant build
ant obfuscate
ant build-release
```

- `ant build`: development JAR and app image. No production obfuscation.
- `ant obfuscate`: production compilation plus ProGuard shrinking, optimization and obfuscation.
- `ant build-release`: complete production build and native installer for the current platform.
- `ant test`: regression suite.

## Production hardening

The release path does not reuse the normal debug compilation.

1. Sources are compiled with `debug=false`.
2. Release metadata such as build user and build date is omitted from the production JAR.
3. ProGuard 7.10.0 shrinks unused bytecode, performs optimization passes and obfuscates application symbols according to `proguard.pro`.
4. Required reflection/JNI entry points for SQLite, PDFBox and WareStat are explicitly preserved.
5. ProGuard mapping, usage, seeds and effective configuration are written only under `build/private/`.
6. The release fails if required application classes, SQLite entry points or PDF resources disappear after protection.
7. SQLite native libraries for unrelated operating systems and CPU architectures are removed from each platform build.
8. `jlink` creates a reduced Java 21 runtime with debug information, native JDK command-line tools, headers and man pages removed and runtime resources compressed.
9. `jpackage` creates a self-contained application image and the final platform-native installer.

The application source under `src/` is not rewritten by this process.

## Installer outputs

GitHub Actions builds three independent release artifacts:

- `WareStat-<version>-windows-x64.exe`
- `WareStat-<version>-macos-arm64.dmg`
- `WareStat-<version>-macos-x64.dmg`

Each installer is accompanied by a SHA-256 checksum.

Windows uses a stable upgrade UUID, installs per user by default, supports choosing the install directory and lets the user choose Start Menu/Desktop shortcuts. macOS uses the stable bundle identifier `com.warestat.app`.

The packaged application is self-contained; the user does not need Java installed.

## Private build files

Never distribute anything from:

```
build/private/
```

In particular, the ProGuard mapping file allows production stack traces to be retraced and must remain private.

The public/customer-facing output should contain only the installer, its checksum, and the legal files included inside the application package.

## GitHub Actions

`.github/workflows/release-hardening.yml` performs:

```
regression tests
    -> protected JAR validation
    -> Windows x64 / macOS arm64 / macOS x64 packaging
    -> leak checks
    -> SHA-256 generation
    -> installer artifacts
```

Third-party GitHub Actions used by this workflow are pinned to exact commit SHAs rather than floating version tags.

## Code signing

The CI currently produces unsigned installers. Before a public commercial release, configure platform signing credentials:

- Windows: Authenticode code-signing certificate
- macOS: Apple Developer ID Application certificate plus Apple notarization credentials

Signing and notarization are separate from ProGuard. They establish publisher identity and make tampering detectable by the operating system.

## Troubleshooting

If the normal development build works but the protected build fails, inspect the ProGuard warnings before adding keep rules. Do not disable warnings globally.

If the installer build fails after the protected JAR succeeds, check the platform-specific `jpackage`/WiX output.

If a customer reports an obfuscated production stack trace, use the mapping file generated for that exact WareStat version. Mapping files from another release are not interchangeable.
