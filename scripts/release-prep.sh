#!/usr/bin/env bash
# Build, test and verify a signed YFT release APK, then stage it with its checksum and notes.
#
# Signing comes from YFT_RELEASE_* environment variables or the untracked keystore.properties
# (docs/RELEASE.md). The script never reads or prints the passwords itself; Gradle fails the
# build when signing is missing, so there is no unsigned "release" fallback.
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: bash scripts/release-prep.sh [options]

Options:
  --skip-tests                Skip lint and unit tests (use only right after a full CI run).
  --expected-cert-sha256 HEX  Owner's release certificate SHA-256 (or $YFT_EXPECTED_CERT_SHA256).
  --previous-apk FILE         Previously released APK for the upgrade-compatibility check.
  --out-dir DIR               Output directory (default dist/<versionName>).
USAGE
  exit 2
}

skip_tests=0
verify_args=()
out_dir=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests) skip_tests=1; shift ;;
    --expected-cert-sha256|--previous-apk)
      [[ $# -ge 2 ]] || usage
      verify_args+=("$1" "$2")
      shift 2
      ;;
    --out-dir) [[ $# -ge 2 ]] || usage; out_dir="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

root="$(git rev-parse --show-toplevel)"
cd "$root"

property() {
  sed -n "s/^$1=//p" gradle.properties | tail -n 1
}

version_name="$(property 'yft\.versionName')"
version_code="$(property 'yft\.versionCode')"
[[ -n "$version_name" && "$version_code" =~ ^[0-9]+$ ]] \
  || { echo "gradle.properties needs yft.versionName and a numeric yft.versionCode" >&2; exit 1; }
out_dir="${out_dir:-dist/$version_name}"
notes="docs/release/$version_name.md"

echo "== Video Downloader $version_name (versionCode $version_code)"
grep -q "^## \[$version_name\]" CHANGELOG.md \
  || { echo "CHANGELOG.md has no '## [$version_name]' section" >&2; exit 1; }
[[ -f "$notes" ]] || { echo "Release notes $notes are missing" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "WARNING: tracked files have uncommitted changes; the APK will not match a commit." >&2
fi

# Separate Gradle invocations keep peak memory low enough for 4 GiB machines.
gradle=(./gradlew --no-daemon)
"${gradle[@]}" clean
if [[ "$skip_tests" -eq 0 ]]; then
  "${gradle[@]}" lintDebug testDebugUnitTest \
    :core-model:test :extractor-api:test :extractor-generic:test :extractor-sites:test
fi
"${gradle[@]}" -Pyft.requireReleaseSigning=true :app:assembleRelease

apk="app/build/outputs/apk/release/app-release.apk"
[[ -f "$apk" ]] || { echo "Signed release APK not found at $apk" >&2; exit 1; }
rm -rf "$out_dir"
bash scripts/verify-release-apk.sh --expected-version "$version_name" --out-dir "$out_dir" \
  ${verify_args[@]+"${verify_args[@]}"} "$apk"

info() {
  sed -n "s/^$1=//p" "$out_dir/release-info.txt"
}

{
  cat "$notes"
  echo
  echo "## Verify the download"
  echo
  echo "- File: \`$(info apk_file)\` ($(info apk_bytes) bytes)"
  echo "- SHA-256: \`$(info apk_sha256)\`"
  echo "- Signing certificate SHA-256: \`$(info certificate_sha256)\`"
  echo "- Source commit: \`$(info source_commit)\`"
} >"$out_dir/release-notes.md"

echo "== Staged in $out_dir:"
ls -l "$out_dir"
echo "Next: install-test it (scripts/device-smoke-test.sh), then attach the APK, SHA256SUMS and"
echo "release-notes.md to a draft GitHub release. Publishing needs ALLOW_RELEASE=true."
