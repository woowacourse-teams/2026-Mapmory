#!/bin/sh
set -eu

ios_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/mapmory-crashlytics-tests.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM

swiftc "$ios_dir/FirebaseAnalyticsLogger.swift" \
    "$ios_dir/tests/CrashlyticsEventContextTests.swift" \
    -o "$test_dir/crashlytics-context-tests"
"$test_dir/crashlytics-context-tests"
