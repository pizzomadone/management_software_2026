# Application Icons

This directory contains the application icons used for different platforms.

## Required Icon Files

### For Windows (.ico)
- **File**: `warestat.ico`
- **Format**: Windows Icon (ICO)
- **Recommended sizes**: 16x16, 32x32, 48x48, 256x256 pixels
- **Tool**: You can use online converters like:
  - https://convertio.co/png-ico/
  - https://icoconvert.com/
  - https://www.aconvert.com/icon/png-to-ico/

### For macOS (.icns)
- **File**: `warestat.icns`
- **Format**: Apple Icon Image (ICNS)
- **Recommended sizes**: 16x16, 32x32, 128x128, 256x256, 512x512 pixels
- **Tool**:
  - Use `iconutil` (built-in macOS tool)
  - Or online: https://cloudconvert.com/png-to-icns

### For Linux (.png)
- **File**: `warestat.png`
- **Format**: PNG image
- **Recommended size**: 512x512 pixels (will be scaled automatically)

## How to Create Icons

### Option 1: From Existing Logo/Image

1. Start with a high-resolution image (at least 512x512 px)
2. For Windows:
   - Upload to https://convertio.co/png-ico/
   - Select multiple sizes: 16, 32, 48, 256
   - Download as `warestat.ico`
   - Place in this directory

3. For macOS:
   - Upload to https://cloudconvert.com/png-to-icns
   - Download as `warestat.icns`
   - Place in this directory

4. For Linux:
   - Just save your PNG image as `warestat.png` (512x512)
   - Place in this directory

### Option 2: Using Design Tools

1. **Photoshop/GIMP**: Create your icon design at 512x512
2. Export as PNG
3. Use online converters for .ico and .icns formats

### Option 3: Quick Placeholder Icon

If you need a quick placeholder:
```bash
# Create a simple colored square icon using ImageMagick (if installed)
convert -size 512x512 xc:#4A90E2 -gravity center -pointsize 200 -fill white -annotate +0+0 "WS" warestat.png
```

Then convert to .ico and .icns using online tools.

## Icon Naming Convention

**IMPORTANT**: The icon files MUST be named:
- `warestat.ico` (Windows)
- `warestat.icns` (macOS)
- `warestat.png` (Linux)

The build system automatically detects your platform and uses the appropriate icon file.

## Testing Your Icons

After placing the icon files:

1. Run the build:
   ```bash
   ant build          # For development
   ant build-release  # For production
   ```

2. Check the executable:
   - **Windows**: `dist/WareStat-dev/WareStat-dev.exe` or `dist/WareStat/WareStat.exe`
   - **macOS**: `dist/WareStat-dev.app` or `dist/WareStat.app`
   - **Linux**: Executable in `dist/WareStat-dev/` or `dist/WareStat/`

3. Right-click the executable and check if your icon appears

## Current Status

⚠️ **No icon files found yet**

Please add your icon files to this directory:
- [ ] `warestat.ico` (Windows)
- [ ] `warestat.icns` (macOS)
- [ ] `warestat.png` (Linux)

Once added, the build system will automatically include them in your executable.
