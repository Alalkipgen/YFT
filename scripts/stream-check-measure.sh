#!/usr/bin/env bash
# Phase 1.2 N2 phone measurement (MASTER_KEY_PHASE1_2_PLAN.md §2 "Live check V in more detail"):
# runs the opt-in StreamCheckMeasureTest on a connected phone. One public, embeddable video's
# stream is read (first 1 MiB at most) with the right n, an unchanged n, a wrong n and a wrong
# signature; the report gives each status class, bytes and time, and the n rule it suggests
# (JUDGES_N, SIG_ONLY or PENDING). Never part of default CI.
#
#   bash scripts/stream-check-measure.sh                  # default video
#   VIDEO=<video ID> bash scripts/stream-check-measure.sh
set -euo pipefail
cd "$(dirname "$0")/.."
APP_ID=com.alal.yft.debug
OUT=${OUT:-build/own-solver}
REMOTE=/sdcard/Android/data/$APP_ID/files/own-solver

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
adb get-state >/dev/null

./gradlew -Pyft.masterCapture=true -Pyft.ownSolver=true :app:installDebug :app:installDebugAndroidTest

extra=(-e yft.streamCheckMeasure 1)
[[ -n "${VIDEO:-}" ]] && extra+=(-e yft.streamCheckMeasure.video "$VIDEO")

status=0
adb shell am instrument -w -r "${extra[@]}" \
  -e class com.alal.yft.master.StreamCheckMeasureTest \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT-measure-instrument.log" || status=$?
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED" "$OUT-measure-instrument.log" && status=1

mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT/" >/dev/null
latest=$(ls -t "$OUT"/stream-check-measure-*.json 2>/dev/null | head -n 1 || true)
[[ -n "$latest" ]] || { echo "no measurement report was written" >&2; exit 1; }
# The report must never carry an address.
if grep -q '://' "$latest"; then echo "report contains an address: $latest" >&2; exit 1; fi
echo "stream-check measurement: $latest"
cat "$latest"; echo
[[ $status -eq 0 ]] || echo "FAIL: the right values did not open the stream; the n rule stays PENDING" >&2
exit $status
