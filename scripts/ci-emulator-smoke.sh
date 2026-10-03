#!/usr/bin/env bash
# Collect diagnostics before android-emulator-runner shuts down the emulator.
set -uo pipefail

root="$(git rev-parse --show-toplevel)" || exit 1
cd "$root" || exit 1
temporary="${RUNNER_TEMP:-/tmp}"
output="$temporary/yft-smoke"
raw="$temporary/yft-smoke-raw-logcat.txt"
application_id="com.alal.yft.debug"
device_screenshots="/sdcard/Android/data/$application_id/files/smoke-screenshots"
mkdir -p "$output/screenshots" || exit 1
adb logcat -c || true
adb shell rm -rf "$device_screenshots" || true

# AGP normally uninstalls the APKs, deleting their external files before adb can pull them.
# Keep them installed only until diagnostics are collected; the AVD cache is already clean.
./gradlew --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  :app:connectedDebugAndroidTest
test_result=$?

adb logcat -d >"$raw"
log_result=$?
adb pull "$device_screenshots/." "$output/screenshots"
pull_result=$?
python3 scripts/ci-smoke-diagnostics.py --raw-log "$raw" --output-dir "$output" \
  --reports app/build/reports/androidTests/connected \
  --reports app/build/outputs/androidTest-results/connected
diagnostic_result=$?

adb uninstall "$application_id.test" >/dev/null 2>&1 || true
adb uninstall "$application_id" >/dev/null 2>&1 || true
if [[ "$pull_result" -ne 0 ]]; then
  echo '::error::Could not collect emulator screenshots'
fi
if [[ "$test_result" -ne 0 ]]; then exit "$test_result"; fi
if [[ "$log_result" -ne 0 ]]; then
  echo '::error::Could not capture emulator logcat'
  exit "$log_result"
fi
if [[ "$pull_result" -ne 0 ]]; then exit "$pull_result"; fi
exit "$diagnostic_result"