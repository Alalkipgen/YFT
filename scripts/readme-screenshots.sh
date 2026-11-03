#!/usr/bin/env bash
# README screenshots (runbook: docs/TESTING.md, Phone and emulator tests): runs the opt-in
# ReadmeScreenshotsTest on a connected emulator and copies its pictures to docs/screenshots/.
# Use a clean emulator: the test downloads a sample video into the app's Library. CI runs this in
# readme-screenshots.yml on a Pixel-sized emulator.
#
#   bash scripts/readme-screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."
APP_ID=com.alal.yft.debug
REMOTE=/sdcard/Android/data/$APP_ID/files/readme-screenshots
OUT=${OUT:-docs/screenshots}
LOG=build/readme-screenshots-instrument.log
SHOTS=(home browser-found download-sheet downloads library settings home-dark)

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
adb get-state >/dev/null

# A tidy status bar: a fixed clock, full Wi-Fi and battery, no notification icons.
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile hide
demo notifications -e visible false
trap 'demo exit || true' EXIT

./gradlew --no-daemon :app:installDebug :app:installDebugAndroidTest
adb shell rm -rf "$REMOTE" || true
mkdir -p build
status=0
adb shell am instrument -w -r -e yft.readmeScreenshots 1 \
  -e class com.alal.yft.smoke.ReadmeScreenshotsTest \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee "$LOG" || status=$?
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED" "$LOG" && status=1

mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT/" >/dev/null || status=1
adb uninstall "$APP_ID.test" >/dev/null 2>&1 || true
for shot in "${SHOTS[@]}"; do
  [[ -s "$OUT/$shot.png" ]] || { echo "missing screenshot: $shot.png" >&2; status=1; }
done
ls -l "$OUT"
exit $status
