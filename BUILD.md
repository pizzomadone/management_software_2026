# WareStat Build System Guide

This guide explains how to use the automated Ant build system to create runnable JAR files and native executables with optional yGuard obfuscation.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Eclipse Integration](#eclipse-integration)
- [Build Targets](#build-targets)
- [Understanding Obfuscation](#understanding-obfuscation)
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

- **yGuard** (automatically downloaded from Maven Central on first obfuscation build)
- Internet connection (for automatic yGuard download)

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
- **Development JAR**: `dist/WareStat-1.2.jar` (~35 MB)
- **Development executable**: `dist/WareStat-dev/` (~85 MB, optimized JRE, no obfuscation)
- **Production JAR**: `dist/WareStat-1.2-obfuscated.jar` (~30 MB, obfuscated)
- **Production executable**: `dist/WareStat/` (~85 MB, optimized JRE + obfuscation)
- **Mapping file**: `dist/yguard-log.xml` (for decoding obfuscated stack traces)

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
- Class names, method names, field names → Single letters (`A`, `B`, `C`)
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
public class A {
    private B a;

    public void b() {
        a = C.a(d);
    }
}
```

**Your source files stay exactly as you wrote them!**

### Benefits of Obfuscation

✅ **Protects intellectual property** - Makes reverse engineering difficult
✅ **Reduces JAR size** - Removes unused code (can reduce size by 20-40%)
✅ **Optimizes bytecode** - Can improve performance
✅ **Protects algorithms** - Your business logic stays confidential

### Why yGuard?

yGuard offers several advantages:
- **Free for commercial use** (Apache 2.0 License)
- **Works with any JDK** - No special requirements
- **Simple configuration** - Designed specifically for Ant
- **Active maintenance** - Regular updates
- **Perfect for Swing apps** - Handles GUI applications well

### Custom JRE with jlink

**Both development and production builds** use **jlink** to create a custom, optimized JRE:

**Benefits:**
- **60% smaller** - ~50MB instead of ~200MB
- **Still self-contained** - Users don't need Java installed
- **Faster startup** - Less code to load
- **More secure** - Smaller attack surface

**How it works:**
- Analyzes your application's Java module dependencies
- Includes only required modules (java.base, java.desktop, java.sql, etc.)
- Strips debug symbols, man pages, and headers
- Compresses the result

**Size comparison (with vs without jlink):**
```
Without jlink:      ~235 MB (full JRE)
With jlink:         ~85 MB  (custom JRE)
Savings:            ~150 MB (63% reduction!)
```

**Note:** Both `ant build` and `ant build-release` use jlink for optimized executables.

### Using the Mapping File

When customers report errors, their stack traces will be obfuscated:

```
Exception in thread "main" java.lang.NullPointerException
    at A.b(Unknown Source)
    at C.d(Unknown Source)
```

**Decode using the mapping file**:

The mapping file `dist/yguard-mapping.txt` contains the translation between original and obfuscated names. You can manually look up the obfuscated names, or use yGuard's built-in tools.

**IMPORTANT**: Keep `dist/yguard-mapping.txt` safe for each release!

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

### Error: "yGuard download failed"

**Cause**: Network/firewall blocking Maven Central

**Solution**: Download yGuard manually:
1. Download from: https://repo1.maven.org/maven2/com/yworks/yguard/4.1.0/yguard-4.1.0.jar
2. Save to: `tools/yguard.jar`
3. Run build again

### Error: "Main class not found" when running JAR

**Cause**: Main class name incorrect in `build.properties`

**Solution**:
1. Open `build.properties`
2. Verify: `main.class=MainWindow`
3. Run `ant clean build`

### Application crashes after obfuscation

**Cause**: yGuard obfuscated classes that should be kept

**Solution**:
1. Check `dist/yguard-log.xml` to see what was renamed
2. Edit `build.xml` obfuscate target to add keep rules
3. Example - keep a specific class:
   ```xml
   <keep>
       <class classes="private" methods="private" fields="private">
           <patternset>
               <include name="YourClassName"/>
           </patternset>
       </class>
   </keep>
   ```
4. Run `ant clean build-release` again

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

### Error: "Module not found" at runtime (production build)

**Cause**: jlink didn't include a required Java module

**Solution**:
1. Check error message for missing module name
2. Edit `build.xml` in the `create-custom-runtime` target
3. Add the missing module to `--add-modules` list:
   ```xml
   <arg value="java.base,java.desktop,java.sql,java.logging,java.naming,java.management,MISSING_MODULE_HERE"/>
   ```
4. Run `ant clean build-release` again

**Common additional modules:**
- `java.xml` - XML processing
- `java.prefs` - Preferences API
- `jdk.crypto.ec` - Elliptic Curve cryptography

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
```

### Adjust Obfuscation Level

Edit `build.xml` in the `obfuscate` target:

**More aggressive (obfuscate more)**:
```xml
<keep>
    <class classes="none" methods="none" fields="none">
        <patternset>
            <include name="${main.class}"/>
        </patternset>
    </class>
</keep>
```

**Less aggressive (keep more readable)**:
```xml
<keep>
    <class classes="protected" methods="protected" fields="protected">
        <patternset>
            <include name="**.*"/>
        </patternset>
    </class>
</keep>
```

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
├── WareStat-1.2.jar                    # Development JAR (~35 MB)
├── WareStat-1.2-obfuscated.jar         # Production JAR (~30 MB, obfuscated)
├── yguard-log.xml                       # yGuard mapping & log file (KEEP THIS!)
├── WareStat-dev/                       # Development executable (~85 MB total)
│   ├── bin/
│   │   └── WareStat-dev                # Launcher (Linux/Mac)
│   │   └── WareStat-dev.exe            # Launcher (Windows)
│   ├── lib/
│   │   └── app/
│   │       └── WareStat-1.2.jar        # Bundled JAR
│   └── runtime/                        # Custom JRE via jlink (~50 MB)
└── WareStat/                           # Production executable (~85 MB total)
    ├── bin/
    │   └── WareStat                    # Launcher (Linux/Mac)
    │   └── WareStat.exe                # Launcher (Windows)
    ├── lib/
    │   └── app/
    │       └── WareStat-1.2-obfuscated.jar  # Bundled JAR (obfuscated)
    └── runtime/                        # Custom JRE via jlink (~50 MB)
```

**Size comparison:**
- Development build: ~85 MB (custom JRE via jlink)
- Production build: ~85 MB (custom JRE via jlink + obfuscation)
- JAR only: ~35 MB (requires Java to be installed)
- Without jlink: ~235 MB (if using full JRE)

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

## Advantages of yGuard Over ProGuard

| Feature | yGuard | ProGuard |
|---------|--------|----------|
| **License** | Apache 2.0 (free commercial) | Apache 2.0 (free commercial) |
| **JDK Requirements** | Any JDK/JRE works | Requires JDK with jmods |
| **Ant Integration** | Native, simple | Complex configuration |
| **Configuration** | Inline or separate file | Separate .conf file needed |
| **Swing Support** | Excellent | Good, needs tuning |
| **Setup Complexity** | Low | High |
| **File Size** | ~1MB | ~9MB |

---

## Need Help?

- **Check Console Output**: Most errors have clear messages
- **Test JAR First**: Always test JAR before creating executable
- **Verify Dependencies**: Ensure `lib/` contains all required JARs
- **Check Java Version**: Requires JDK 21+
- **Read Log Files**: Use yguard-log.xml to understand what was obfuscated

---

**Happy Building! 🚀**
