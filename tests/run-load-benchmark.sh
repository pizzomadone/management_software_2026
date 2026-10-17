#!/usr/bin/env bash
# Isolated benchmark: never touches the default WareStat profile.
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repo_dir"

products="${1:-20000}"
photos="${2:-$products}"
backups="${3:-true}"
restore="${4:-false}"
if ! [[ "$products" =~ ^[0-9]+$ && "$photos" =~ ^[0-9]+$ ]]; then
  echo "Products and photos must be integers" >&2; exit 2
fi
if [[ "$backups" != "true" && "$backups" != "false" ]] ||
   [[ "$restore" != "true" && "$restore" != "false" ]]; then
  echo "Backups and restore must be true/false" >&2; exit 2
fi

mkdir -p build
scratch="$(mktemp -d "$repo_dir/build/load-benchmark-XXXXXX")"
report_dir="$repo_dir/build/load-benchmark-output"
mkdir -p "$scratch/home" "$scratch/classes" "$report_dir"
touch "$scratch/home/warestat-load-test.marker"
rm -f "$report_dir/results.csv" "$report_dir/dataset.txt" "$report_dir/run.log"
if [[ "${WARESTAT_KEEP_DATA:-0}" == "1" ]]; then
  echo "Dataset will be retained at: $scratch"
else
  trap 'rm -rf -- "$scratch"' EXIT
fi

javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$javac_bin" --release 21 -encoding UTF-8 -cp 'lib/*' -d "$scratch/classes" \
  src/*.java tests/WareStatLoadBenchmark.java

# The synthetic data never reaches ~/.warestat or any user profile.
"$java_bin" -Xmx3g -Djava.awt.headless=true \
  -Duser.home="$scratch/home" -Dtest.output="$report_dir" \
  -cp "$scratch/classes:lib/*:resources" WareStatLoadBenchmark \
  "$products" "$photos" "$backups" "$restore" 2>&1 | tee "$report_dir/run.log"

echo "CSV report: $report_dir/results.csv"
if [[ "${WARESTAT_KEEP_DATA:-0}" == "1" ]]; then
  echo "Synthetic WareStat home (can open using a separate app run): $scratch/home"
fi
