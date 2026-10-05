#!/usr/bin/env bash
set -euo pipefail

: "${NIK_HOME:?NIK_HOME is required}"

mkdir -p native-out
config="$PWD/native-config"
out="$PWD/native-out"

jawt_static="$(find "$NIK_HOME" -type f -name 'libjawt.a' -print -quit)"
jawt_dynamic="$(find "$NIK_HOME" -type f -name 'libjawt.dylib' -print -quit)"

echo "JAWT static archive: ${jawt_static:-<none>}"
echo "JAWT dynamic library: ${jawt_dynamic:-<none>}"

jawt_link_args=()
if [[ -n "$jawt_static" ]]; then
  echo "Inspecting static JAWT symbols"
  file "$jawt_static" || true
  nm -g "$jawt_static" 2>/dev/null | grep -E 'JAWT_GetAWT|JAWT_' || true

  # Force the archive member that owns JAWT_GetAWT into the executable and
  # export the symbol so SwingWebView's dlsym(RTLD_DEFAULT, ...) can resolve it.
  jawt_link_args+=("-H:NativeLinkerOption=-Wl,-force_load,$jawt_static")
  jawt_link_args+=("-H:NativeLinkerOption=-Wl,-exported_symbol,_JAWT_GetAWT")
elif [[ -n "$jawt_dynamic" ]]; then
  echo "No static JAWT archive; inspecting dynamic JAWT candidate"
  file "$jawt_dynamic" || true
  nm -g "$jawt_dynamic" 2>/dev/null | grep -E 'JAWT_GetAWT|JAWT_' || true
  otool -L "$jawt_dynamic" || true
else
  echo "Liberica NIK contains no standalone libjawt.a or libjawt.dylib"
fi

common_args=(
  --no-fallback
  -J-Xmx6g
  -Djava.awt.headless=false
  "-Dorg.sqlite.lib.exportPath=$out"
  "-H:ConfigurationFileDirectories=$config"
  -H:+ReportExceptionStackTraces
  "${jawt_link_args[@]}"
  "-H:IncludeResources=^(i18n/.*|icons/.*|fonts/.*|META-INF/services/.*|LICENSE-THIRD-PARTY.txt|NOTICE.txt)$"
)

(
  cd native-out
  native-image "${common_args[@]}" -o WareStat-native -cp "../build/release/classes:../lib/*" MainWindow
  native-image "${common_args[@]}" -o WareStat-native-probe -cp "../build/graal-probe-classes:../build/release/classes:../lib/*" GraalProbe
)

if [[ -n "$jawt_dynamic" ]]; then
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
