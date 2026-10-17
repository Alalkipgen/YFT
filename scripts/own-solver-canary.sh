#!/usr/bin/env bash
# Phase 1.1 S6 phone canary (MASTER_KEY_PHASE1_1_PLAN.md §3 S6, runbook §6): runs the opt-in
# OwnSolverCanaryTest on a connected phone or emulator. Today's YouTube player goes through main's
# ejs runner and Master's own runner, both in their real WebView engines; the report says
# pass/fail per kind (n, sig) with timings. Never part of default CI. The computer-side check of
# every player build is `node scripts/own-solver-canary.mjs`.
#
#   bash scripts/own-solver-canary.sh                 # today's player
#   PLAYER=<player ID> bash scripts/own-solver-canary.sh
set -euo pipefail
cd "$(dirname "$0")/.."
APP_ID=com.alal.yft.debug
OUT=${OUT:-build/own-solver}
REMOTE=/sdcard/Android/data/$APP_ID/files/own-solver

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
adb get-state >/dev/null

./gradlew -Pyft.masterCapture=true -Pyft.ownSolver=true :app:installDebug :app:installDebugAndroidTest

extra=(-e yft.ownSolverCanary 1)
[[ -n "${PLAYER:-}" ]] && extra+=(-e yft.ownSolverCanary.player "$PLAYER")

status=0
adb shell am instrument -w -r "${extra[@]}" \
  -e class com.alal.yft.master.OwnSolverCanaryTest \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT-instrument.log" || status=$?
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED" "$OUT-instrument.log" && status=1

mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT/" >/dev/null
latest=$(ls -t "$OUT"/own-solver-canary-*.json 2>/dev/null | head -n 1 || true)
[[ -n "$latest" ]] || { echo "no canary report was written" >&2; exit 1; }
# The report must never carry an address.
if grep -q '://' "$latest"; then echo "report contains an address: $latest" >&2; exit 1; fi
echo "own-solver phone canary: $latest"
cat "$latest"; echo
[[ $status -eq 0 ]] || echo "FAIL: follow MASTER_KEY_PHASE1_1_PLAN.md §6 (runbook)" >&2
exit $status
