#!/usr/bin/env bash

set -euo pipefail

PACKAGE="com.mapmory.android"
ACTIVITY="${PACKAGE}/.MainActivity"
SERIAL="${1:-}"
RUNS="${RUNS:-5}"

if [ -z "$SERIAL" ]; then
    echo "사용법: RUNS=5 $0 <adb-serial>" >&2
    exit 2
fi

case "$RUNS" in
    ''|*[!0-9]*)
        echo "RUNS는 양의 정수여야 합니다: $RUNS" >&2
        exit 2
        ;;
esac

if [ "$RUNS" -lt 1 ]; then
    echo "RUNS는 1 이상이어야 합니다: $RUNS" >&2
    exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "adb를 찾을 수 없습니다. Android SDK platform-tools를 PATH에 추가하세요." >&2
    exit 1
fi

if ! adb -s "$SERIAL" get-state >/dev/null 2>&1; then
    echo "연결된 기기를 찾을 수 없습니다: $SERIAL" >&2
    echo "adb devices -l 로 serial을 확인하세요." >&2
    exit 1
fi

measure_mode() {
    mode="$1"
    totals=""

    echo "[$mode] ${RUNS}회 측정"

    if [ "$mode" = "hot" ]; then
        adb -s "$SERIAL" shell monkey -p "$PACKAGE" 1 >/dev/null
    fi

    run=1
    while [ "$run" -le "$RUNS" ]; do
        if [ "$mode" = "cold" ]; then
            adb -s "$SERIAL" shell am force-stop "$PACKAGE"
        fi

        output="$(adb -s "$SERIAL" shell am start -W -n "$ACTIVITY" | tr -d '\r')"
        total="$(printf '%s\n' "$output" | awk -F': *' '$1 == "TotalTime" { print $2; exit }')"

        if [ -z "$total" ]; then
            echo "$output" >&2
            echo "TotalTime을 읽지 못했습니다." >&2
            exit 1
        fi

        echo "${mode}_run=${run} total_ms=${total}"
        totals="$totals $total"
        run=$((run + 1))
    done

    sorted="$(printf '%s\n' $totals | sort -n)"
    median="$(printf '%s\n' "$sorted" | awk -v count="$RUNS" '
        NR == int((count + 1) / 2) { lower = $1 }
        NR == int(count / 2) + 1 { upper = $1 }
        END {
            if (count % 2 == 1) print lower
            else printf "%.0f", (lower + upper) / 2
        }
    ')"
    maximum="$(printf '%s\n' "$sorted" | tail -n 1)"
    average="$(printf '%s\n' $totals | awk '{ sum += $1 } END { printf "%.0f", sum / NR }')"

    echo "${mode}_summary average_ms=${average} median_ms=${median} max_ms=${maximum}"
}

echo "package=$PACKAGE serial=$SERIAL runs=$RUNS"
measure_mode cold
measure_mode hot
