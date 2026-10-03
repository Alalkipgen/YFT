#!/usr/bin/env bash
# Collect diagnostics before android-emulator-runner shuts down the emulator.
set -uo pipefail

root="$(git rev-parse --show-toplevel)" || exit 1
cd "$root" || exit 1
temporary="${RUNNER_TEMP:-/tmp}"
output="$temporary/yft-smoke"
raw="$temporary/yft-smoke-raw-logcat.txt"
mkdir -p "$output"
adb logcat -c || true
adb shell rm -rf /sdcard/Android/data/com.alal.yft.debug/files/smoke-screenshots || true

./gradlew --no-daemon :app:connectedDebugAndroidTest
test_result=$?

adb logcat -d >"$raw"
log_result=$?
adb pull /sdcard/Android/data/com.alal.yft.debug/files/smoke-screenshots \
  "$output/screenshots" >/dev/null 2>&1 || true
python3 scripts/ci-smoke-diagnostics.py --raw-log "$raw" --output-dir "$output" \
  --reports app/build/reports/androidTests/connected \
  --reports app/build/outputs/androidTest-results/connected
diagnostic_result=$?

if [[ "$test_result" -ne 0 ]]; then exit "$test_result"; fi
if [[ "$log_result" -ne 0 ]]; then
  echo '::error::Could not capture emulator logcat'
  exit "$log_result"
fi
exit "$diagnostic_result"