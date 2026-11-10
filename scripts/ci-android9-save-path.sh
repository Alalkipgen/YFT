#!/usr/bin/env bash
# The save path on Android 9 (API 28): YFT saves in its own storage there, so the folder, the file
# in it and Android's reading of it are checked on a real system of that version. Only the tests
# that run on API 28 are asked for: the rest of the suite needs Android 10 or newer.
set -uo pipefail

root="$(git rev-parse --show-toplevel)" || exit 1
cd "$root" || exit 1
temporary="${RUNNER_TEMP:-/tmp}"
raw="$temporary/yft-android9-logcat.txt"
mkdir -p "$temporary" || exit 1
adb logcat -c || true

./gradlew --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.alal.yft.download.AppPrivateSaveInstrumentedTest \
  :app:connectedDebugAndroidTest
test_result=$?

adb logcat -d >"$raw" || true
grep -E 'AndroidRuntime|FATAL|AppPrivateSave' "$raw" | tail -n 40 || true
if [[ "$test_result" -ne 0 ]]; then
  echo '::error::The Android 9 save path failed'
  find app/build/reports/androidTests/connected -name '*.html' -print -quit || true
fi
exit "$test_result"
