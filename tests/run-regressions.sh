#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repo_dir"
test_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
test_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
mkdir -p build
test_dir="$(mktemp -d "$repo_dir/build/regression-XXXXXX")"
mkdir -p "$test_dir/classes" "$test_dir/home"
"$test_javac" --release 21 -encoding UTF-8 -cp 'lib/*' -d "$test_dir/classes" src/*.java tests/*.java
"$test_java" -Djava.awt.headless=true -Duser.home="$test_dir/home" \
    -cp "$test_dir/classes:lib/*:resources" RuntimeNoticeRegressionTest > "$test_dir/notice-results.log" 2>&1 || {
    cat "$test_dir/notice-results.log"
    exit 1
}
sed -n '/^PASS:/p' "$test_dir/notice-results.log"
"$test_java" -Djava.awt.headless=true -Duser.home="$test_dir/home" \
    -cp "$test_dir/classes:lib/*:resources" EulaAcceptanceRegressionTest > "$test_dir/eula-results.log" 2>&1 || {
    cat "$test_dir/eula-results.log"
    exit 1
}
sed -n '/^PASS:/p' "$test_dir/eula-results.log"
"$test_java" -Djava.awt.headless=true -Duser.home="$test_dir/home" \
    -cp "$test_dir/classes:lib/*:resources" StartupDatabaseRecoveryRegressionTest
"$test_java" -Djava.awt.headless=true -Duser.home="$test_dir/home" \
    -cp "$test_dir/classes:lib/*:resources" ResponsiveToolBarRegressionTest
"$test_java" -Djava.awt.headless=true -Duser.home="$test_dir/home" -Dtest.output="$test_dir/evidence" \
    -cp "$test_dir/classes:lib/*:resources" RegressionTest > "$test_dir/results.log" 2>&1 || {
    cat "$test_dir/results.log"
    exit 1
}
sed -n '/^PASS /p; /^Regression checks:/p' "$test_dir/results.log"
printf 'Full log and synthetic PDF evidence: %s\n' "$test_dir"
