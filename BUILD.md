# WareStat build and release guide

WareStat uses Apache Ant and JDK 21. Development builds remain easy to debug; production builds use a separate hardened pipeline.

## Requirements

- JDK 21 with `jlink` and `jpackage`
- Apache Ant
- Windows installer builds: WiX Toolset 3.14.1
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

Third-party GitHub Actions used by this workflow are pinned to exact commit SHAs rather than floating version tags. The Windows job also rejects WiX versions older than 3.14.1 and installs the pinned Chocolatey package 3.14.1.20250415 if needed.

A tag such as `v1.3.0` must match `app.version`. After all three platform artifacts and checksums pass validation, the workflow creates a draft GitHub Release only; it does not publish it automatically.

## Reference release sizes

The successful CI run on 2026-10-05 produced these measurements. MiB values use 1,048,576 bytes.

| Stage | Windows x64 | macOS arm64 | macOS x64 |
| --- | ---: | ---: | ---: |
| Release input JAR | 28,488,825 B (27.17 MiB) | 28,487,639 B (27.17 MiB) | 28,487,639 B (27.17 MiB) |
| Protected JAR | 19,615,254 B (18.71 MiB) | 19,614,086 B (18.71 MiB) | 19,614,086 B (18.71 MiB) |
| Platform JAR after SQLite native pruning | 6,168,367 B (5.88 MiB) | 6,208,472 B (5.92 MiB) | 6,289,046 B (6.00 MiB) |
| Custom Java runtime | 50,493,986 B (48.15 MiB) | 53,626,708 B (51.14 MiB) | 55,582,349 B (53.01 MiB) |
| Application image | 58,102,715 B (55.41 MiB) | 61,263,975 B (58.43 MiB) | 63,320,142 B (60.39 MiB) |
| Final installer | 43,469,824 B (41.46 MiB) | 42,686,773 B (40.71 MiB) | 44,140,087 B (42.10 MiB) |

The platform-specific JAR is about 78% smaller than the release input JAR. The main reduction comes from ProGuard plus removal of SQLite native binaries for unrelated platforms.

## PDFBox dependency decision

WareStat uses PDFBox document creation, fonts and image APIs. Apache PDFBox supports using the core `pdfbox` artifact with its required transitive dependencies instead of `pdfbox-app`. For this release the dependency is not split: ProGuard already removes unreachable PDFBox tooling and the final platform JAR is only about 5.9-6.0 MiB. Replacing the committed input JAR would therefore mainly reduce build input size while increasing dependency-management and PDF regression risk. Reconsider this only when an end-to-end installer benchmark shows a material download-size reduction and invoices, proformas, reports, Unicode fonts and PDF images all pass the same smoke tests.

## GraalVM Native Image evaluation

GraalVM Native Image is not used by the production pipeline. WareStat combines Swing/AWT, SQLite JNI, resource bundles and a native WebView. Native Image uses closed-world analysis and requires reachability metadata when dynamic access such as JNI, reflection, resources or bundles cannot be resolved statically. A production conversion would therefore need platform-specific metadata and explicit validation of both native libraries.

The current ProGuard + jlink + jpackage path is retained because it passes the same protected-runtime checks on Windows x64, macOS arm64 and macOS x64 without changing application behavior. A Native Image target should remain a separate experiment unless it can pass the full regression and packaging suite without WebView or JNI workarounds. GraalVM Community Edition is compatible with commercial use, but it is not required or distributed by this release pipeline.

## Commercial build tooling

The production path uses free tooling compatible with commercial application distribution: OpenJDK/Temurin, Ant, ProGuard, jdeps, jlink, jpackage and WiX Toolset 3.14.1. ProGuard's license explicitly permits processing commercial applications. WiX 3.14.1 predates the Open Source Maintenance Fee introduced with WiX v6. No paid obfuscator or packer is used.

## Code signing

The CI produces unsigned test installers when signing secrets are absent. Before a public commercial release, configure platform signing credentials:

- Windows: Authenticode code-signing certificate
- macOS: Apple Developer ID Application certificate plus Apple notarization credentials

Signing and notarization are separate from ProGuard. They establish publisher identity and make tampering detectable by the operating system.

## Troubleshooting

If the normal development build works but the protected build fails, inspect the ProGuard warnings before adding keep rules. Do not disable warnings globally.

If the installer build fails after the protected JAR succeeds, check the platform-specific `jpackage`/WiX output.

If a customer reports an obfuscated production stack trace, use the mapping file generated for that exact WareStat version. Mapping files from another release are not interchangeable.
