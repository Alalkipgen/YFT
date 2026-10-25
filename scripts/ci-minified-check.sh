#!/usr/bin/env bash
# Decision 30 (R8, the release commit): the shrunk builds are run, not only compiled. Installs the
# #12 normal preview (com.alal.yft.preview) and the #12 trial (com.alal.yft.trial) built in this
# job, drives each through its screens (app/src/androidTest/.../minified/MinifiedBuildCheckTest.kt,
# run from the debug test APK) and reads their logs. Each check's RESULT line becomes an
# annotation (no address: the test drops them); a line of either app's own naming an R8-type error
# (a class, method or field the shrinker removed or renamed) a warning. The job fails when either
# app crashes or the run itself fails; a live site's answer to the data centre never fails it.
set -uo pipefail

root="$(git rev-parse --show-toplevel)" || exit 1
cd "$root" || exit 1
out="${RUNNER_TEMP:-/tmp}/yft-minified"
host="com.alal.yft.debug"
shots="/sdcard/Android/data/$host/files/minified-check"
mkdir -p "$out/screenshots" || exit 1
status=0

for apk in app/build/outputs/apk/preview/app-preview.apk \
  app/build/outputs/apk/previewTrial/app-previewTrial.apk \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk; do
  if ! adb install -r -g "$apk" >/dev/null; then
    echo "::error::Could not install $(basename "$apk")"
    exit 1
  fi
done
adb shell rm -rf "$shots" || true
adb logcat -G 16M >/dev/null 2>&1 || true

for target in com.alal.yft.trial com.alal.yft.preview; do
  name="${target##*.}"
  uid="$(adb shell pm list packages -U "$target" | tr -d '\r' | sed -n "s/^package:$target uid:\([0-9]*\).*/\1/p" | head -n 1)"
  log="$out/raw-$name.txt"
  adb logcat -c || true
  adb logcat -v threadtime -v uid >"$log" 2>/dev/null &
  reader=$!
  adb shell am instrument -w -r -e yft.minified "$target" \
    -e class com.alal.yft.minified.MinifiedBuildCheckTest \
    "$host.test/androidx.test.runner.AndroidJUnitRunner" >"$out/instrument-$name.txt" 2>&1
  sleep 2
  kill "$reader" 2>/dev/null || true
  wait "$reader" 2>/dev/null || true
  if ! python3 - "$target" "${uid:-0}" "$log" "$out/instrument-$name.txt" "$out/summary-$name.txt" <<'PY'
import re
import sys

target, uid, log_path, instrument_path, summary_path = sys.argv[1:6]
uid = int(uid or 0)
uid_names = {f" {uid} ", f" u0_a{uid - 10000} "} if uid >= 10000 else set()
R8 = re.compile(
    r"ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|"
    r"AbstractMethodError|IncompatibleClassChangeError|VerifyError|ExceptionInInitializerError|"
    r"InstantiationException|ClassCastException"
)
ADDRESS = re.compile(r"https?://\S+")


def clean(text):
    return ADDRESS.sub("<address>", text).strip()[:1500]


def escape(text, title=False):
    text = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if title:
        text = text.replace(":", "%3A").replace(",", "%2C")
    return text


lines = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
own = [line for line in lines if any(name in line for name in uid_names)] if uid_names else []
if not own:
    own = [line for line in lines if "com.alal.yft" in line]
failed = False
summary = []
for line in lines:
    marker = "YftMinifiedCheck: RESULT "
    if marker in line:
        result = clean(line.split(marker, 1)[1])
        head, _, body = result.partition(" :: ")
        parts = head.split()
        title = " ".join(parts[1:3]) if len(parts) >= 3 else head
        print(f"::notice title={escape('minified ' + title, True)}::{escape(body)}")
        summary.append(result)
for index, line in enumerate(lines):
    if "FATAL EXCEPTION" in line:
        block = lines[index:index + 14]
        if any(f"Process: {target}," in entry for entry in block):
            failed = True
            text = clean("\n".join(entry.split(": ", 1)[-1] for entry in block))
            print(f"::error title={escape(target + ' crashed', True)}::{escape(text)}")
            summary.append("CRASH " + text)
errors = []
for line in own:
    if R8.search(line) and "YftMinifiedCheck" not in line:
        entry = clean(line.split(": ", 1)[-1])
        if entry not in errors:
            errors.append(entry)
# Read beside the RESULT lines: an app's own caught error shows in its lines too; a crash fails.
for entry in errors[:4]:
    print(f"::warning title={escape(target + ' R8-type line', True)}::{escape(entry)}")
    summary.append("R8? " + entry)
report = open(instrument_path, encoding="utf-8", errors="replace").read()
if "INSTRUMENTATION_FAILED" in report or "Process crashed" in report or not re.search(r"OK \(\d+ test", report):
    failed = True
    tail = clean("\n".join(report.splitlines()[-12:]))
    print(f"::error title={escape(target + ' check did not run', True)}::{escape(tail)}")
    summary.append("RUN " + tail)
if not any(entry.startswith(target) for entry in summary):
    print(f"::warning title={escape(target, True)}::No RESULT line was logged")
open(summary_path, "w", encoding="utf-8").write("\n".join(summary) + "\n")
sys.exit(1 if failed else 0)
PY
  then
    status=1
  fi
  rm -f "$log"
done

adb pull "$shots/." "$out/screenshots" >/dev/null 2>&1 || echo "::warning::Could not collect the check's screenshots"
{
  echo "### Minified build check (#12 normal and #12 trial, shrunk with R8)"
  echo
  echo '```'
  cat "$out"/summary-*.txt 2>/dev/null
  echo '```'
} >>"${GITHUB_STEP_SUMMARY:-/dev/null}"
rm -f "$out"/instrument-*.txt
adb uninstall "$host.test" >/dev/null 2>&1 || true
exit "$status"
