# WareStat

Professional Business Management System

## Features

- **Customer Management** - Complete customer database with contact information
- **Product Catalog** - Comprehensive product management with stock tracking
- **Order Processing** - Customer order management with payment status tracking
- **Invoice Generation** - Create and export professional PDF invoices
- **Supplier Management** - Manage suppliers, orders, and price lists
- **Warehouse Control** - Stock movements, notifications, and minimum stock levels
- **Sales Reports & Analytics** - Detailed sales reports and business insights
- **Backup & Restore** - Database backup and recovery system

## System Requirements

- Java 21 or higher
- macOS, Windows, or Linux
- Minimum 4GB RAM recommended
- 500MB available disk space

## Building the Application

### Automated Build (Recommended)

Use the included Ant build system to create runnable JARs and native executables:

```bash
# Development build (JAR + native executable)
ant build

# Production build (obfuscated JAR + native executable)
ant build-release
```

**Eclipse Users**: See [BUILD.md](BUILD.md) for complete Eclipse integration guide.

**Full Documentation**: See [BUILD.md](BUILD.md) for detailed build instructions, ProGuard obfuscation, and troubleshooting.

### Manual Build (Alternative)

```bash
# Compile
javac -d bin -cp "lib/*:src" src/*.java

# Run
java -cp "bin:lib/*" MainWindow
```

## Database Location

The application database is stored at:
- **macOS**: `~/Library/Application Support/WareStat/data.db`
- **Windows**: `%APPDATA%\WareStat\data.db`
- **Linux**: `~/.local/share/WareStat/data.db`

## Third-Party Libraries

This software uses the following open-source libraries:

### Apache PDFBox 3.0.6
- **Purpose**: PDF generation for invoices
- **License**: Apache License 2.0
- **Copyright**: © 1999-2024 The Apache Software Foundation
- **Website**: https://pdfbox.apache.org/

### SQLite JDBC 3.50.3.0
- **Purpose**: Database connectivity
- **License**: Apache License 2.0
- **Copyright**: © Taro L. Saito
- **Website**: https://github.com/xerial/sqlite-jdbc

See `LICENSE-THIRD-PARTY.txt` for the complete license text and `NOTICE.txt` for additional copyright information.

## License

The third-party libraries included with this software are licensed under the Apache License 2.0.
See `LICENSE-THIRD-PARTY.txt` for details.

## Barcode Support

The software supports barcode scanning for faster product lookup and order processing:

1. Connect a USB barcode scanner (works as keyboard input)
2. Enter barcode numbers in the product "Code" field
3. Use scanner in search fields to quickly find products
4. Supports EAN-13, UPC, Code 128, and QR codes

See documentation for complete barcode integration guide.

## Support

For issues, questions, or feature requests, please contact your software provider.
