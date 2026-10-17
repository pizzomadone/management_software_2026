# WareStat 1.3

**Professional business management software for Windows and macOS.**

WareStat brings customers, products, orders, invoices, suppliers, warehouse
operations, reports, and backups together in one desktop application.

## What is new in version 1.3

- **Six interface languages:** Italian, Spanish, Brazilian Portuguese, French,
  German, and Polish, selectable on first launch and from Settings.
- **WareStat Online:** an embedded page for product news and communications,
  loaded from <https://www.warestat.com/discover/> at startup.
- **Calendar date picker:** localized date selection in orders, invoices,
  supplier workflows, price lists, and report filters.
- **Consistent currency formatting:** the currency selected in Settings is used
  across dashboards, commercial documents, reports, and warehouse views.
- **VAT corrections:** valid 0% rates are respected and invoice generation no
  longer forces a fixed VAT rate.
- **Improved PDF invoices:** company logos are loaded directly from their
  configured path and monetary values use the configured currency.
- **Safer backups:** SQLite snapshots are created consistently in WAL mode and
  checked for integrity before completion.
- **Stronger stock protection:** order and supplier-order changes correctly
  preserve stock, reservations, and movement history.
- **Duplicate-invoice prevention:** invoices generated from an order retain the
  source order and the same order cannot generate multiple invoices.
- **Versioned database migrations:** existing installations are upgraded through
  ordered, transactional migrations recorded in `schema_migrations`.

## Main features

- Customer and supplier management
- Product catalog with barcode lookup
- Customer orders and payment tracking
- Invoice creation and PDF export
- Supplier orders and price lists
- Stock quantities, reservations, movements, and minimum-stock notifications
- Sales reports, warehouse reports, and advanced statistics
- Database backup and restore
- Runtime localization with translated actions, states, tables, and tooltips

## WareStat Online

Discover is implemented with `ca.weblite.webview.swing.WebViewComponent`, which
uses the operating system web view and supports JavaScript, iframe, HTML5 video,
and YouTube embeds inside Swing.
The panel has no address bar and provides only back, forward, and refresh controls.

The page is always loaded from:

```text
https://www.warestat.com/discover/
```

Requests use a WareStat-specific user agent containing the application version
and technical runtime information such as Java version, operating system,
architecture, language, locale, time zone, screen size, and selected theme. It
does not include customers, products, orders, invoices, company details, file
paths, or other business data.

If the system web view or network is unavailable, WareStat continues to run and the panel
shows an unavailable message instead of blocking application startup.

## Localization

Runtime localization is managed by `I18n`. Translation catalogs are stored in:

```text
resources/i18n/messages_<locale>.properties
```

The available locale codes are:

| Language | Locale |
| --- | --- |
| Italian | `it` |
| Spanish | `es` |
| Brazilian Portuguese | `pt_BR` |
| French | `fr` |
| German | `de` |
| Polish | `pl` |

Displayed statuses are translated without changing the underlying values used
by application logic or stored in the database.

## Database upgrades

Database upgrades are managed by `DatabaseMigrationManager`. Each ordered
migration runs in a transaction and is recorded in `schema_migrations`, ensuring
that it is applied only once.

### Data model change — 2026-09-01

Invoices generated from orders now store their source in `invoices.order_id`.
A unique partial index prevents more than one invoice from being generated for
the same order. Existing invoices remain unchanged and receive no inferred order
association.

## System requirements

- Java 21 or later
- Windows or macOS
- 4 GB RAM recommended
- 500 MB of free disk space
- Internet connection for WareStat Online
- `lib/webview-1.0.10.jar`

No JavaFX, Maven, JCEF, or embedded Chromium installation is required.

## Dependencies

Place the required JAR files in `lib/` before building:

- **Apache PDFBox 3.0.6** — invoice and report PDF generation
- **SQLite JDBC 3.50.3.0** — SQLite database connectivity
- **WebLite WebView 1.0.10** — embedded Discover page

The WebView JAR is kept in `lib/` with PDFBox and SQLite and is copied beside the
application JAR and into the jpackage input directory by Ant.

See `LICENSE-THIRD-PARTY.txt` and `NOTICE.txt` for license and attribution
information.

## Building the application

WareStat uses Apache Ant.

### Development build

```bash
ant build
```

This produces the JAR and a native development application image.

### Production build

```bash
ant build-release
```

This produces an obfuscated production JAR and native application image.

### Production build with license viewer

```bash
ant build-release-with-license
```

For detailed build, packaging, Eclipse, yGuard, and troubleshooting instructions,
see [BUILD.md](BUILD.md).

### Manual compilation

On macOS:

```bash
javac -d bin -cp "lib/*:src" src/*.java
java -cp "bin:resources:lib/*:target/dependency/*" MainWindow
```

On Windows, replace the classpath separator `:` with `;`.

## Application data

WareStat stores its database and configuration files in a per-user directory:

- **Windows:** `%USERPROFILE%\AppData\Local\WareStat\`
- **macOS:** `~/Library/Application Support/WareStat/`

The live company data is stored in `data.wsbq`: **standard SQLite**
with Xerial JDBC and an **exclusive SQLite connection** ( `PRAGMA locking_mode=EXCLUSIVE` ).
When WareStat runs, SQLite sees its original header and updates pages normally,
including JPEG BLOBs in `product_images.image_data`. When WareStat shuts
down cleanly, it closes SQLite and changes **only the first 16 bytes** to the
WareStat signature. At the next startup, WareStat accepts either signature
and restores the original SQLite bytes *before opening the connection*.

An unclean stop may leave the normal SQLite header on disk, allowing SQLite
to recover any WAL/journal transaction data. The header is sealed only after
SQLite has closed and no nonempty WAL or journal remains. This mechanism merely
discourages casual opening in generic SQLite tools: **it is not encryption and
does not prevent someone from reversing the 16-byte modification**.

Backup, export, and New Archive output are single-file **`.wsbq`
SQLite snapshots**, sealed with the WareStat signature. **Only a signed,
current-schema WareStat `.wsbq` or the original version-1.2 masked-ZIP
WareStat `.wsbq` archive may be imported**. Bare `.db` files, renamed
generic SQLite files, unsealed `.wsbq` files and sealed obsolete schemas
are rejected by the import API. An extracted legacy SQLite file is converted
*internally* as part of verified version-1.2 backup import.
Photos (optimized JPEG, max. 800px and 150KB) are embedded as BLOBs;
thumbnail previews are derived and cached in RAM. A manual backup/export
can exclude photo rows in the *copy*, never in the live database.
Automatic backups include photos.

On first startup without `data.wsbq`, WareStat initializes a **new
empty database**. It does not open, import or migrate any old `data.db` and
does not delete that file or its external images. To transfer version-1.2
data, explicitly select the official **version-1.2 WareStat `.wsbq` backup**
under Import Backup; validation and migration then embed its JPEGs as BLOBs.
A standard SQLite header is tolerated only for the *active internal*
`data.wsbq` during recovery from an interrupted shutdown, not for user imports.
WareStat can create consistent backups from the exclusive owner connection
using SQLite `VACUUM INTO`, rather than trying to open a second
connection to the locked live file. Incremental page reclamation is run
opportunistically after backups.

The SQLite WAL transaction journal may temporarily exist as
`data.wsbq-wal` while WareStat is running; this is normal and must
not be deleted if it contains data. In EXCLUSIVE WAL mode SQLite can avoid
creating the shared-memory `-shm` file. Future multi-workstation WareStat
Pro access should use a server as the exclusive database owner rather than
allowing several PCs to edit the `.wsbq` directly over a network.
Backup files are kept in the configured backup directory.

## Barcode support

USB barcode scanners that operate as keyboards can be used in product code and
search fields. WareStat can therefore work with common values such as EAN-13,
UPC, Code 128, and QR code contents without requiring a scanner-specific driver.

## License

WareStat is proprietary software © 2026 WareStat (<https://www.warestat.com/>).
It is provided “AS IS” without warranty. The application generates `LICENSE.txt`
in its per-user data directory; see that file for the complete terms and
`LICENSE-THIRD-PARTY.txt` for the licenses of bundled components.

## Support

For support, product news, and release communications, visit
<https://www.warestat.com/discover/> or contact your software provider.


## Optional large-data benchmark (manual only)

The benchmark is on branch `test/wsbq-20k-load-benchmark`,
based on `feature/sqlite-wsbq-image-blobs`. It is not a production
migration. It always runs with a **temporary isolated user.home** protected by a
marker file and refuses to overwrite an existing database. A private repository
does **not** need to be made public to run the workflow; usage/billing limits
may nevertheless prevent hosted Actions from starting.

To run on macOS/Linux with JDK 21:

```bash
bash tests/run-load-benchmark.sh 20000 20000 true false
```

Arguments: `<products> <photos> <run_backups> <run_restore>`.
For a quick smoke test without heavyweight file copies:

```bash
bash tests/run-load-benchmark.sh 200 200 false false
```

A full 20k run creates 1,000 suppliers, 5,000 customers, 30,000 customer
orders, 15,000 supplier orders, 20,000 draft invoices, 100,000 movements,
40,000 FIFO lots and 60,000 movement-to-lot consumption references. It
generates 20,000 separate valid JPEG **BLOB rows** (16 reproducible visual
variants reused with unique photo filenames to keep generation fast).
It measures SQL search/pagination/update/aggregate performance, actual
thumbnail decoding, backup with and without photos, validation, header
seal/reopen, database size and SQLite integrity. The document examples are
synthetic rather than a complete business-transaction simulation; tests
of pricing and sales correctness remain in the standard regression suite.

CSV/diagnostics are written to `build/load-benchmark-output/`.
By default, the multi-GB sandbox is removed after execution. To retain the
database for **manual UI scrolling** on the same machine, run:

```bash
WARESTAT_KEEP_DATA=1 bash tests/run-load-benchmark.sh 20000 20000 true false
```

The command prints the sandbox path (including the isolated `home`).
Only launch an application build against this home explicitly if you wish to
browse the synthetic company. Do not copy the fixture over real WareStat data.

A pull request touching the benchmark runs a **small 200-product, 200-photo smoke test**.
To use **Actions → WareStat 20k load benchmark → Run workflow** for the
full dataset, GitHub requires this workflow file to exist on the repository's
**default branch first**; keeping it on this experimental branch alone is
not sufficient for the manual-run button. Once available, it uploads `results.csv`, `dataset.txt`
and `run.log` for 7 days. The 20k configuration is deliberately **not** tied to pushes
or pull requests, since large data generation consumes significant runner
disk, CPU and time. Start with 200/200 or 1000/1000 if hosted Actions has not
been tested on this repository. Setting `restore=true` adds an expensive full
restore with an additional safety copy; allow extra disk space.

**This benchmark is not a GUI frame-rate test**. Real user-interface lag,
table scrolling, and paint events should be measured with the retained
dataset on macOS/Windows. Results are hardware and storage dependent.

### Verified on public GitHub Actions — 20,000 product run (2026-10-17)

The full test completed successfully on Ubuntu 24.04, Java 21:
[GitHub Actions full-run log](https://github.com/pizzomadone/management_software_2026/actions/runs/39011550526).

- **20,000** product rows, **20,000** valid JPEG BLOB rows (~100 KiB each; 16 varied JPEG templates), **100,000** movements, **40,000** FIFO lots and **60,000** consumption links.
- SQLite database: **2,105,462,784 bytes**.
- Exact-code lookup (mean per lookup): **0.0086 ms**; 100-row catalog page: **52.7 ms**; name search: **3.20 ms**; individual product update: **0.187 ms**.
- 200 JPEG thumbnail decodes: **1.82 s**; SQL dashboard query: **7.09 ms** mean.
- A second full-scale run also succeeded, with corrected thumbnail cache measurement: 100 hot thumbnail lookups averaged **0.556 ms** each ([second full run](https://github.com/pizzomadone/management_software_2026/actions/runs/39011756168)).
- Full backup: **9.70 s**, no-photo backup: **9.77 s**; full backup validation **17.06 s**, restore **28.62 s**.
- Startup reopening the sealed ~2.1 GB database: **1.97 s**; SQLite integrity checks passed both before and after restore.
- Regression suite on the same branch passed **181 tests, 0 failures**: [Actions regression run](https://github.com/pizzomadone/management_software_2026/actions/runs/39011460851).

**Note:** The temporary auto-triggered one-shot 20k workflow used for the
successful run was deleted afterward to prevent accidental repeated high-cost
jobs. The normal lightweight PR smoke workflow remains. Java benchmark startup,
query and image metrics above measure GitHub's Linux runner only, *not* GUI
scrolling/frame times on a user's Mac. A post-run cleanup commit also fixed the
"cached thumbnails" benchmark to warm the correct thumbnails before sampling;
the full-run cache metric from this first run should not be treated as a
true cache-hit measurement.
