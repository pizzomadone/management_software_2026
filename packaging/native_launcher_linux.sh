#!/bin/sh
set -eu

APP_DIR="${WARESTAT_HOME:-/opt/warestat}"
if [ ! -x "$APP_DIR/WareStat-native" ]; then
  APP_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
fi

export LD_LIBRARY_PATH="$APP_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
exec "$APP_DIR/WareStat-native" "-Dorg.sqlite.lib.exportPath=$APP_DIR" "$@"
