#!/usr/bin/env bash
# Install, launch and (optionally) upgrade test for a YFT APK on one connected Android device or
# emulator (Android 7.0+, USB debugging on). Feature checks stay manual: see docs/RELEASE.md.
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: bash scripts/device-smoke-test.sh [--fresh] [--upgrade-from OLD.apk] <apk>

  --fresh              Uninstall com.alal.yft first. This deletes its settings and app-private
                       downloads on the device.
  --upgrade-from OLD   Install OLD, launch it, then update to <apk> in place and check that it
                       stayed the same installation (needs --fresh so OLD installs cleanly).
USAGE
  exit 2
}

package="com.alal.yft"
fresh=0
old_apk=""
apk=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh) fresh=1; shift ;;
    --upgrade-from) [[ $# -ge 2 ]] || usage; old_apk="$2"; shift 2 ;;
    -h|--help) usage ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) [[ -z "$apk" ]] || usage; apk="$1"; shift ;;
  esac
done
[[ -n "$apk" && -f "$apk" ]] || usage
[[ -z "$old_apk" || -f "$old_apk" ]] || { echo "Not found: $old_apk" >&2; exit 1; }
[[ -z "$old_apk" || "$fresh" -eq 1 ]] || { echo "--upgrade-from needs --fresh" >&2; exit 1; }

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS  $*"
}

command -v adb >/dev/null 2>&1 || fail "adb is not on PATH (Android SDK platform-tools)"
devices="$(adb devices | sed '1d' | awk '$2 == "device"' | wc -l | tr -d ' ')"
[[ "$devices" == "1" ]] || fail "connect exactly one device or emulator (found $devices)"
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$sdk" =~ ^[0-9]+$ && "$sdk" -ge 24 ]] || fail "device API level '$sdk' is below 24"
pass "device API $sdk ($(adb shell getprop ro.product.model | tr -d '\r'))"

package_field() {
  adb shell dumpsys package "$package" | tr -d '\r' | sed -n "s/.*$1=\([^ ]*\).*/\1/p" | head -n 1
}

install_apk() {
  local output
  output="$(adb install -r "$1" 2>&1)" || true
  if [[ "$output" != *Success* ]]; then
    if [[ "$output" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
      fail "an installed $package has a different signer; rerun with --fresh"
    fi
    fail "install of $1 failed: $output"
  fi
  pass "installed $(basename "$1") (versionCode $(package_field versionCode))"
}

launch_app() {
  adb logcat -c >/dev/null 2>&1 || true
  adb shell am force-stop "$package"
  adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
    || fail "could not launch $package"
  sleep 6
  if adb logcat -d -s AndroidRuntime:E | grep -q "Process: $package"; then
    adb logcat -d -s AndroidRuntime:E | head -n 40 >&2
    fail "$package crashed after launch"
  fi
  [[ -n "$(adb shell pidof "$package" | tr -d '\r')" ]] || fail "$package is not running"
  pass "launched without a crash"
}

if [[ "$fresh" -eq 1 ]]; then
  adb uninstall "$package" >/dev/null 2>&1 || true
  pass "removed any previous $package"
fi

if [[ -n "$old_apk" ]]; then
  install_apk "$old_apk"
  launch_app
  old_code="$(package_field versionCode)"
  first_install="$(package_field firstInstallTime)"
  install_apk "$apk"
  new_code="$(package_field versionCode)"
  [[ "$new_code" -gt "$old_code" ]] || fail "versionCode did not increase ($old_code -> $new_code)"
  [[ "$(package_field firstInstallTime)" == "$first_install" ]] \
    || fail "the update replaced the installation instead of upgrading it"
  pass "in-place upgrade $old_code -> $new_code kept the same installation"
else
  install_apk "$apk"
fi
launch_app
echo "RESULT: install/launch checks passed. Continue with the manual checklist in docs/RELEASE.md."
