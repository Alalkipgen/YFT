#!/usr/bin/env bash
# v1.0.2 item 0 (the owner, 2026-10-29): the CI runner's Gradle memory, from this one place.
#
# gradle.properties keeps the sizes of the agents' 4 GiB computers (docs/FIX_ADD_PLAN.md). Two of
# Part 1's runs ran out of memory on the runner with them, not in the code: checkpoint validation
# 40911946099 (the Kotlin compiler) and Preview APK 40917475008 (R8, :app:minifyPreviewWithR8).
# Every workflow that runs Gradle runs this script right after its setup-gradle step. It writes the
# two lines below to the runner's Gradle user home gradle.properties, whose values win over the
# project's; it keeps every other line of that file and changes no other Gradle setting.
#
#   bash scripts/ci-gradle-memory.sh               write the two lines (a ::notice:: shows them)
#   bash scripts/ci-gradle-memory.sh --watch FILE  until stopped (run it in the background): note
#                                                  each Gradle and Kotlin daemon the job starts and
#                                                  its memory flags in FILE
#   bash scripts/ci-gradle-memory.sh --report FILE a ::notice:: per daemon FILE notes; a daemon with
#                                                  other sizes: an ::error:: and exit 1
set -euo pipefail

GRADLE_JVMARGS="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"
KOTLIN_DAEMON_JVMARGS="-Xmx2g -XX:MaxMetaspaceSize=512m"
TITLE="CI Gradle memory"

usage() {
  echo "Usage: bash scripts/ci-gradle-memory.sh [--watch FILE | --report FILE]" >&2
  exit 2
}

write_properties() {
  local home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  local file="$home/gradle.properties"
  mkdir -p "$home"
  touch "$file"
  # An older value of either key goes (with its continuation lines); every other line stays.
  awk '
    skipping { skipping = /\\$/; next }
    /^[ \t]*(org\.gradle\.jvmargs|kotlin\.daemon\.jvmargs)([ \t]*[=:]|[ \t])/ { skipping = /\\$/; next }
    { print }
  ' "$file" > "$file.tmp"
  {
    printf 'org.gradle.jvmargs=%s\n' "$GRADLE_JVMARGS"
    printf 'kotlin.daemon.jvmargs=%s\n' "$KOTLIN_DAEMON_JVMARGS"
  } >> "$file.tmp"
  mv "$file.tmp" "$file"
  echo "::notice title=$TITLE::$file: org.gradle.jvmargs=$GRADLE_JVMARGS; kotlin.daemon.jvmargs=$KOTLIN_DAEMON_JVMARGS"
}

# One look at the processes: each Gradle or Kotlin daemon not noted yet gets a line
# "kind<TAB>pid<TAB>its -Xmx, -XX:MaxMetaspaceSize and -Dfile.encoding flags".
note_daemons() {
  local log="$1" proc="$2" file pid args kind flags
  # The dots are escaped, so this grep's own command line never matches itself.
  while IFS= read -r file; do
    pid="${file%/cmdline}"
    pid="${pid##*/}"
    [[ -z "${NOTED[$pid]:-}" ]] || continue
    args="$(tr '\0' '\n' < "$file" 2>/dev/null)" || continue
    case "$args" in
      *org.gradle.launcher.daemon.bootstrap.GradleDaemon*) kind="Gradle daemon" ;;
      *org.jetbrains.kotlin.daemon.KotlinCompileDaemon*) kind="Kotlin daemon" ;;
      *) continue ;;
    esac
    NOTED[$pid]=1
    flags="$(grep -E '^-(Xmx|XX:MaxMetaspaceSize=|Dfile\.encoding=)' <<<"$args" | tr '\n' ' ' || true)"
    printf '%s\t%s\t%s\n' "$kind" "$pid" "${flags% }" >> "$log"
  done < <(grep -lsaE 'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon|org\.jetbrains\.kotlin\.daemon\.KotlinCompileDaemon' \
    "$proc"/[0-9]*/cmdline 2>/dev/null || true)
}

watch_daemons() {
  local log="$1"
  # Tests point these at a made-up /proc and stop after so many looks (0: until stopped).
  local proc="${CI_GRADLE_MEMORY_PROC:-/proc}" polls="${CI_GRADLE_MEMORY_POLLS:-0}" count=0
  declare -gA NOTED=()
  : > "$log"
  while :; do
    note_daemons "$log" "$proc"
    count=$((count + 1))
    if (( polls > 0 && count >= polls )); then
      break
    fi
    sleep 1
  done
}

# A JVM size (-Xmx4g, 1024m, 2097152k, 4294967296) in bytes, to compare 2g with 2048m.
bytes() {
  local value="${1,,}" number unit
  number="${value%[kmgt]}"
  unit="${value:${#number}}"
  [[ "$number" =~ ^[0-9]+$ ]] || { echo "-1"; return; }
  case "$unit" in
    k) echo $((number * 1024)) ;;
    m) echo $((number * 1024 * 1024)) ;;
    g) echo $((number * 1024 * 1024 * 1024)) ;;
    t) echo $((number * 1024 * 1024 * 1024 * 1024)) ;;
    *) echo "$number" ;;
  esac
}

# The value the JVM uses: the last one of a flag, or "" without it.
last_value() {
  local prefix="$1" flag value=""
  local -a flags
  read -ra flags <<<"$2"
  for flag in ${flags[@]+"${flags[@]}"}; do
    if [[ "$flag" == "$prefix"* ]]; then
      value="${flag#"$prefix"}"
    fi
  done
  echo "$value"
}

# "" when the flags give each size (and the encoding) of the wanted jvmargs, else what differs.
differences() {
  local wanted="$1" flags="$2" prefix want have out=""
  for prefix in -Xmx -XX:MaxMetaspaceSize= -Dfile.encoding=; do
    want="$(last_value "$prefix" "$wanted")"
    [[ -n "$want" ]] || continue
    have="$(last_value "$prefix" "$flags")"
    if [[ "$prefix" == -Dfile.encoding= ]]; then
      [[ "${have^^}" == "${want^^}" ]] && continue
    elif [[ -n "$have" && "$(bytes "$have")" == "$(bytes "$want")" ]]; then
      continue
    fi
    out+="${out:+, }${prefix}${have:-(none)} instead of ${prefix}${want}"
  done
  echo "$out"
}

report_daemons() {
  local log="$1" kind pid flags wanted key differ status=0 gradle=0 kotlin=0
  if [[ -f "$log" ]]; then
    while IFS=$'\t' read -r kind pid flags; do
      [[ -n "$kind" ]] || continue
      if [[ "$kind" == "Gradle daemon" ]]; then
        wanted="$GRADLE_JVMARGS" key="org.gradle.jvmargs" gradle=$((gradle + 1))
      else
        wanted="$KOTLIN_DAEMON_JVMARGS" key="kotlin.daemon.jvmargs" kotlin=$((kotlin + 1))
      fi
      differ="$(differences "$wanted" "$flags")"
      if [[ -z "$differ" ]]; then
        echo "::notice title=$TITLE::$kind $pid ran with ${flags:-no memory flags} ($key=$wanted)"
      else
        echo "::error title=$TITLE::$kind $pid ran with ${flags:-no memory flags}: $differ ($key=$wanted did not reach it)"
        status=1
      fi
    done < "$log"
  fi
  if (( gradle == 0 )); then
    echo "::warning title=$TITLE::No Gradle daemon was noted (${log}), so nothing shows its memory."
  fi
  if (( kotlin == 0 )); then
    echo "::warning title=$TITLE::No Kotlin daemon was noted: Kotlin compiled nothing or not in its daemon."
  fi
  return "$status"
}

case "${1:-}" in
  "")
    [[ $# -eq 0 ]] || usage
    write_properties
    ;;
  --watch)
    [[ $# -eq 2 ]] || usage
    watch_daemons "$2"
    ;;
  --report)
    [[ $# -eq 2 ]] || usage
    report_daemons "$2"
    ;;
  *)
    usage
    ;;
esac
