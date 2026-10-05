#!/usr/bin/env bash
set -euo pipefail

: "${NIK_HOME:?NIK_HOME is required}"

mkdir -p native-out
config="$PWD/native-config"
out="$PWD/native-out"

jawt_static="$(find "$NIK_HOME" -type f -name 'libjawt.a' -print -quit)"
jawt_dynamic="$(find "$NIK_HOME" -type f -name 'libjawt.dylib' -print -quit)"
awt_lwawt_static="$(find "$NIK_HOME" -type f -name 'libawt_lwawt.a' -print -quit)"
awt_static="$(find "$NIK_HOME" -type f -name 'libawt.a' -print -quit)"

echo "JAWT static archive: ${jawt_static:-<none>}"
echo "JAWT dynamic library: ${jawt_dynamic:-<none>}"
echo "AWT LWAWT static archive: ${awt_lwawt_static:-<none>}"
echo "AWT base static archive: ${awt_static:-<none>}"

native_link_args=()
jawt_mode="none"

if [[ -n "$jawt_static" ]]; then
  jawt_mode="static"
  echo "Using standalone static JAWT archive"
  file "$jawt_static" || true
  nm -g "$jawt_static" 2>/dev/null | grep -E 'JAWT_GetAWT|JAWT_' || true

  native_link_args+=("-H:NativeLinkerOption=-Wl,-force_load,$jawt_static")
  native_link_args+=("-H:NativeLinkerOption=-Wl,-exported_symbol,_JAWT_GetAWT")
elif [[ -n "$awt_lwawt_static" && -n "$awt_static" ]]; then
  jawt_mode="bridge"
  echo "No standalone libjawt.a; building packaging-only JAWT bridge"

  required_symbols=(
    awt_GetDrawingSurface
    awt_FreeDrawingSurface
    awt_Lock
    awt_Unlock
    awt_GetComponent
    awt_CreateEmbeddedFrame
    awt_SetBounds
    awt_SynthesizeWindowActivation
  )
  missing=0
  for symbol in "${required_symbols[@]}"; do
    if nm -g "$awt_lwawt_static" 2>/dev/null | grep -q "[[:space:]]_${symbol}$"; then
      echo "Found AWT bridge dependency: $symbol"
    else
      echo "Missing AWT bridge dependency in libawt_lwawt.a: $symbol" >&2
      missing=1
    fi
  done
  if [[ "$missing" -ne 0 ]]; then
    echo "Cannot build a safe JAWT bridge from this NIK" >&2
    exit 1
  fi

  cc -O2 -fPIC \
    -I"$NIK_HOME/include" \
    -I"$NIK_HOME/include/darwin" \
    -c packaging/jawt_bridge.c \
    -o "$out/jawt_bridge.o"

  file "$out/jawt_bridge.o"
  nm -g "$out/jawt_bridge.o" | grep -E 'JAWT_GetAWT|awt_'

  # Add the bridge object first, then re-scan the two AWT archives so the
  # bridge's awt_* references are resolved even if Native Image already
  # mentioned those archives earlier on the linker command line.
  native_link_args+=("-H:NativeLinkerOption=$out/jawt_bridge.o")
  native_link_args+=("-H:NativeLinkerOption=$awt_lwawt_static")
  native_link_args+=("-H:NativeLinkerOption=$awt_static")
  native_link_args+=("-H:NativeLinkerOption=-Wl,-exported_symbol,_JAWT_GetAWT")
elif [[ -n "$jawt_dynamic" ]]; then
  jawt_mode="dynamic"
  echo "No static JAWT route; bundling the NIK dynamic JAWT library"
  file "$jawt_dynamic" || true
  nm -g "$jawt_dynamic" 2>/dev/null | grep -E 'JAWT_GetAWT|JAWT_' || true
  otool -L "$jawt_dynamic" || true
else
  echo "No usable JAWT implementation was found in Liberica NIK" >&2
  echo "The build will continue so the strict WebView probe records the exact runtime failure." >&2
fi

common_args=(
  --no-fallback
  -J-Xmx6g
  -Djava.awt.headless=false
  "-Dorg.sqlite.lib.exportPath=$out"
  "-H:ConfigurationFileDirectories=$config"
  -H:+ReportExceptionStackTraces
)
common_args+=("${native_link_args[@]}")
common_args+=("-H:IncludeResources=^(i18n/.*|icons/.*|fonts/.*|META-INF/services/.*|LICENSE-THIRD-PARTY.txt|NOTICE.txt)$")

(
  cd native-out
  native-image "${common_args[@]}" -o WareStat-native -cp "../build/release/classes:../lib/*" MainWindow
  native-image "${common_args[@]}" -o WareStat-native-probe -cp "../build/graal-probe-classes:../build/release/classes:../lib/*" GraalProbe
)

echo "Selected JAWT mode: $jawt_mode"
if [[ "$jawt_mode" == "dynamic" ]]; then
  cp "$jawt_dynamic" native-out/libjawt.dylib
fi

for exe in native-out/WareStat-native native-out/WareStat-native-probe; do
  echo "=== JAWT symbols in $exe ==="
  nm -g "$exe" 2>/dev/null | grep 'JAWT_GetAWT' || echo "JAWT_GetAWT not exported"
  otool -L "$exe" || true
done

if [[ -f native-out/libjawt.dylib ]]; then
  echo "=== bundled libjawt.dylib ==="
  nm -g native-out/libjawt.dylib 2>/dev/null | grep 'JAWT_GetAWT' || true
  otool -L native-out/libjawt.dylib || true
fi
