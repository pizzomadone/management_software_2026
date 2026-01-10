# WareStat Build System Guide

This guide explains how to use the automated Ant build system to create runnable JAR files and native executables with optional ProGuard obfuscation.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Eclipse Integration](#eclipse-integration)
- [Build Targets](#build-targets)
- [Understanding Obfuscation](#understanding-obfuscation)
- [Manual ProGuard Download](#manual-proguard-download)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required

1. **JDK 21 or higher** with `jpackage` tool
   - Oracle JDK 21+ or OpenJDK 21+
   - Verify: `java -version` and `jpackage --version`

2. **Apache Ant** (if running from command line)
   - Eclipse has Ant built-in, no separate installation needed
   - For command line: Install from https://ant.apache.org/

3. **Dependencies** (already in `lib/` directory)
   - PDFBox 3.0.6
   - SQLite JDBC 3.50.3.0

### Optional

- **ProGuard** (automatically downloaded on first obfuscation build)
- Internet connection (for automatic ProGuard download)

---

## Quick Start

### Command Line

```bash
# Development build (no obfuscation, fast)
ant build

# Production build (with obfuscation)
ant build-release

# Just create JAR (no executable)
ant jar

# Just create obfuscated JAR
ant obfuscate

# Clean build artifacts
ant clean

# View all available targets
ant help
```

### Eclipse (Recommended)

See [Eclipse Integration](#eclipse-integration) section below.

---

## Eclipse Integration

### Step 1: Configure Ant Builder

1. **Open Project Properties**
   - Right-click on project → **Properties**
   - Navigate to **Builders**

2. **Add New Ant Builder**
   - Click **New...** → Select **Ant Builder** → Click **OK**

3. **Configure the Builder**
   - **Name**: `WareStat Build`
   - **Buildfile**: Click **Browse Workspace...** → Select `build.xml`
   - **Base Directory**: Click **Browse Workspace...** → Select project root

4. **Set Build Targets**
   - **Targets** tab:
     - **Manual Build**: `build` (development build)
     - **Auto Build**: Leave empty or set to `jar` for fast builds
     - **Clean**: `clean`

5. **Configure When to Build** (Optional)
   - **Build Options** tab:
     - Uncheck **During auto builds** if you want manual control
     - Check **After a "Clean"**
     - Check **During manual builds**

6. **Click Apply and OK**

### Step 2: Run the Build

**Option A: Manual Build (Recommended)**
1. Right-click on `build.xml` in Project Explorer
2. Select **Run As** → **Ant Build...**
3. Select targets you want:
   - `build` - Development build (no obfuscation)
   - `build-release` - Production build (with obfuscation)
4. Click **Run**

**Option B: Project Menu**
1. Select project in Project Explorer
2. Go to **Project** → **Build Project**
3. Ant builder will run automatically

**Option C: External Tools**
1. Click the dropdown arrow next to the **External Tools** button (▶️ with briefcase icon)
2. Select your configured Ant builder
3. View progress in Console

### Step 3: Find Your Built Application

After successful build:
- **JAR file**: `dist/WareStat-1.2.jar`
- **Obfuscated JAR**: `dist/WareStat-1.2-obfuscated.jar`
- **Native executable**: `dist/WareStat/` (contains platform-specific launcher)
- **Mapping file**: `dist/mapping.txt` (for decoding obfuscated stack traces)

---

## Build Targets

### Development Builds

| Target | Description | Includes Obfuscation |
|--------|-------------|---------------------|
| `build` | Full development build: JAR + native executable | ❌ No |
| `jar` | Create JAR only | ❌ No |
| `package-exe` | Create native executable from JAR | ❌ No |
| `compile` | Compile Java sources only | N/A |

**When to use**: Daily development, testing, debugging

### Production Builds

| Target | Description | Includes Obfuscation |
|--------|-------------|---------------------|
| `build-release` | Full production build: Obfuscated JAR + native executable | ✅ Yes |
| `obfuscate` | Create obfuscated JAR only | ✅ Yes |
| `package-exe-release` | Create native executable from obfuscated JAR | ✅ Yes |

**When to use**: Release builds for distribution to customers

### Utility Targets

| Target | Description |
|--------|-------------|
| `clean` | Remove all build artifacts |
| `test-jar` | Run the JAR file to test it |
| `help` | Display available targets |

---

## Understanding Obfuscation

### What Gets Obfuscated?

**Your source code** (.java files) **NEVER changes**. Obfuscation only affects:
- Compiled `.class` files inside the output JAR
- Class names, method names, field names → Single letters (`a`, `b`, `c`)
- Your source code remains 100% readable

### Before vs After Obfuscation

**Original Code (what you write)**:
```java
public class DatabaseManager {
    private Connection connection;

    public void connect() {
        connection = DriverManager.getConnection(dbUrl);
    }
}
```

**Obfuscated Bytecode (in JAR, not source)**:
```java
public class a {
    private b c;

    public void d() {
        c = e.f(g);
    }
}
```

**Your source files stay exactly as you wrote them!**

### Benefits of Obfuscation

✅ **Protects intellectual property** - Makes reverse engineering difficult
✅ **Reduces JAR size** - Removes unused code (20-40% smaller)
✅ **Optimizes bytecode** - Can improve performance
✅ **Protects algorithms** - Your business logic stays confidential

### Using the Mapping File

When customers report errors, their stack traces will be obfuscated:

```
Exception in thread "main" java.lang.NullPointerException
    at a.d(Unknown Source)
    at c.f(Unknown Source)
```

**Decode with mapping file**:
```bash
# ProGuard includes a tool to decode stack traces
proguard/bin/retrace.sh dist/mapping.txt stacktrace.txt
```

**Output**:
```
Exception in thread "main" java.lang.NullPointerException
    at DatabaseManager.connect(DatabaseManager.java:45)
    at MainWindow.initialize(MainWindow.java:120)
```

**IMPORTANT**: Keep `dist/mapping.txt` safe for each release!

---

## Manual ProGuard Download

If automatic download fails (due to firewall/proxy), download manually:

1. **Download ProGuard 7.4.2**
   - URL: https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.zip
   - Alternative: https://sourceforge.net/projects/proguard/files/

2. **Extract to tools directory**
   ```bash
   unzip proguard-7.4.2.zip -d tools/
   mv tools/proguard-7.4.2 tools/proguard
   ```

3. **Verify structure**
   ```
   tools/
   └── proguard/
       ├── lib/
       │   └── proguard.jar
       └── bin/
   ```

4. **Run build again**
   ```bash
   ant build-release
   ```

---

## Troubleshooting

### Error: "jpackage not found"

**Solution**: Ensure you have JDK 14+ with jpackage:
```bash
# Check if jpackage is available
jpackage --version

# If not found, download JDK 21+:
# - Oracle JDK: https://www.oracle.com/java/technologies/downloads/
# - OpenJDK: https://adoptium.net/
```

### Error: "ProGuard download failed"

**Solution**: See [Manual ProGuard Download](#manual-proguard-download) section above.

### Error: "Main class not found" when running JAR

**Cause**: Main class name incorrect in `build.properties`

**Solution**:
1. Open `build.properties`
2. Verify: `main.class=MainWindow`
3. Run `ant clean build`

### Application crashes after obfuscation

**Cause**: ProGuard removed or renamed required classes

**Solution**:
1. Check `dist/mapping.txt` to see what was renamed
2. Edit `proguard.conf` to keep the problematic class:
   ```
   -keep class YourClassName {
       *;
   }
   ```
3. Run `ant clean build-release` again

### "OutOfMemoryError" during build

**Cause**: Large dependencies or insufficient heap space

**Solution**: Increase Ant memory in Eclipse:
1. Right-click `build.xml` → **Run As** → **Ant Build...**
2. Go to **JRE** tab
3. Add to **VM Arguments**: `-Xmx2048m`

### Native executable won't start

**Cause**: Missing dependencies or incorrect configuration

**Solution**:
1. Test JAR first: `java -jar dist/WareStat-1.2.jar`
2. If JAR works, check jpackage logs in build output
3. Ensure all dependencies are included in JAR

---

## Customization

### Modify Build Settings

Edit `build.properties`:
```properties
# Change application name/version
app.name=WareStat
app.version=1.2
app.vendor=YourCompany

# Change main class
main.class=MainWindow

# Adjust JVM memory for packaged app
jvm.max.memory=2048m
```

### More Aggressive Obfuscation

Edit `proguard.conf`, uncomment these lines:
```properties
# Rename packages to single name
-repackageclasses 'obf'
-flattenpackagehierarchy 'obf'

# Remove debug logging
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
}
```

**WARNING**: Test thoroughly after enabling these!

### Platform-Specific Executables

To create installer packages instead of app images, change in `build.xml`:

**Windows**:
```xml
<arg value="--type"/><arg value="msi"/>
```

**macOS**:
```xml
<arg value="--type"/><arg value="dmg"/>
```

**Linux**:
```xml
<arg value="--type"/><arg value="deb"/>
<!-- or -->
<arg value="--type"/><arg value="rpm"/>
```

---

## Build Output Structure

```
dist/
├── WareStat-1.2.jar                    # Development JAR
├── WareStat-1.2-obfuscated.jar         # Production JAR (obfuscated)
├── mapping.txt                          # ProGuard mapping file (KEEP THIS!)
└── WareStat/                           # Native application directory
    ├── bin/
    │   └── WareStat                    # Launcher (Linux/Mac)
    │   └── WareStat.exe                # Launcher (Windows)
    ├── lib/
    │   └── app/
    │       └── WareStat-1.2.jar        # Bundled JAR
    └── runtime/                        # Bundled JRE (if using jlink)
```

---

## Eclipse Tips

### Quick Access to Build Commands

1. **Toolbar Button**: Drag Ant builder to toolbar for one-click builds
2. **Keyboard Shortcut**:
   - Window → Preferences → General → Keys
   - Search "Run Last Tool"
   - Assign shortcut (e.g., Ctrl+Shift+R)
3. **Console View**: Click "Display Selected Console" to switch between build outputs

### View Build Output

- **Console View**: Shows build progress and errors
- **Problems View**: Shows compilation errors
- **Ant View**: Window → Show View → Ant
  - Drag `build.xml` here for quick target access

---

## Need Help?

- **Check Console Output**: Most errors have clear messages
- **Test JAR First**: Always test JAR before creating executable
- **Verify Dependencies**: Ensure `lib/` contains all required JARs
- **Check Java Version**: Requires JDK 21+
- **Read Stack Traces**: Use mapping.txt to decode obfuscated errors

---

**Happy Building! 🚀**
