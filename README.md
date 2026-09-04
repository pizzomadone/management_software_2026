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

WareStat Online is implemented as a Swing panel containing JavaFX `WebView`
through `JFXPanel`, so the rest of the desktop interface remains Swing-based.
The panel has no address bar and provides only **Back**, **Forward**, **Refresh**,
and **Open in browser** controls.

The page is always loaded from:

```text
https://www.warestat.com/discover/
```

Requests use a WareStat-specific user agent containing the application version
and technical runtime information such as Java version, operating system,
architecture, language, locale, time zone, screen size, and selected theme. It
does not include customers, products, orders, invoices, company details, file
paths, or other business data.

If JavaFX or the network is unavailable, WareStat continues to run and the panel
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
- Platform-compatible JavaFX SDK for the embedded web panel

JavaFX is loaded at runtime. Without it, all core management features remain
available, but WareStat Online cannot be displayed inside the application.

## Dependencies

Place the required JAR files in `lib/` before building:

- **Apache PDFBox 3.0.6** — invoice and report PDF generation
- **SQLite JDBC 3.50.3.0** — SQLite database connectivity
- **OpenJFX / JavaFX** — embedded WareStat Online page

For JavaFX, use the SDK matching both the build JDK and target operating system,
and include the modules required by WebView and Swing interoperability:
`javafx.base`, `javafx.graphics`, `javafx.controls`, `javafx.web`, and
`javafx.media`, plus `javafx.swing`.

See `LICENSE-THIRD-PARTY.txt` and `NOTICE.txt` for license and attribution
information.

## Building the application

WareStat uses Apache Ant.

### Development build

```bash
ant build
```

This produces the runnable JAR and a native development application image.

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
java -cp "bin:resources:lib/*" MainWindow
```

On Windows, replace the classpath separator `:` with `;`.

## Application data

WareStat stores its database and configuration files in a per-user directory:

- **Windows:** `%USERPROFILE%\AppData\Local\WareStat\`
- **macOS:** `~/Library/Application Support/WareStat/`

The primary database file is `data.db`. Backups are stored in the configured
backup directory, which defaults to the `backups` subdirectory of the WareStat
data directory.

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
