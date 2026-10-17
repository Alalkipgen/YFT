#!/usr/bin/env bash
# R1 canary (MASTER_KEY_PLAN §4.1): runs the opt-in MasterParityLiveTest on a connected phone or
# emulator and pulls its sanitized report (host + content ID only). Never part of default CI:
# server IPs hit sign-in walls. Master never plays a video; with ATTENDED=1 tap Play on each page
# when logcat (tag YftParity) says "play now".
#
#   bash scripts/canary.sh                    # every linked case, unattended
#   ATTENDED=1 PLAY_SEC=60 bash scripts/canary.sh
#   ONLY=tiktok-nasa,x-nasa bash scripts/canary.sh
#   LOCAL_URLS=~/parity-urls.local.json bash scripts/canary.sh   # owner picks; never committed
set -euo pipefail
cd "$(dirname "$0")/.."
APP_ID=com.alal.yft.debug
OUT=${OUT:-build/parity}
REMOTE=/sdcard/Android/data/$APP_ID/files/parity

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
adb get-state >/dev/null

./gradlew -Pyft.masterCapture=true :app:installDebug :app:installDebugAndroidTest

if [[ -n "${LOCAL_URLS:-}" ]]; then
  adb shell mkdir -p "$REMOTE"
  adb push "$LOCAL_URLS" "$REMOTE/parity-urls.local.json" >/dev/null
fi

extra=(-e yft.parity 1 -e yft.parity.attended "${ATTENDED:-0}" -e yft.parity.playSec "${PLAY_SEC:-60}")
[[ -n "${ONLY:-}" ]] && extra+=(-e yft.parity.only "$ONLY")
[[ "${STRICT:-0}" == 1 ]] && extra+=(-e yft.parity.strict 1)

adb shell am instrument -w -r "${extra[@]}" \
  -e class com.alal.yft.master.MasterParityLiveTest \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner"

mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT/" >/dev/null
rm -f "$OUT/parity-urls.local.json"
latest=$(ls -t "$OUT"/parity-report-*.json 2>/dev/null | head -n 1 || true)
[[ -n "$latest" ]] || { echo "no parity report was written" >&2; exit 1; }
# The report must never carry an address.
if grep -q '://' "$latest"; then echo "report contains an address: $latest" >&2; exit 1; fi
echo "parity report: $latest"
