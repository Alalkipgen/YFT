#!/usr/bin/env bash
# Verify a YFT release APK and, with --out-dir, stage it with its SHA-256 checksum.
# The script only reads the APK: it never sees keystores or passwords.
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: bash scripts/verify-release-apk.sh [options] <apk>

Checks package and version, that the APK is not debuggable, zip alignment and the APK signature
(v2 or v3, exactly one signer, not the Android debug certificate).

Options:
  --expected-cert-sha256 HEX  Required signer certificate SHA-256 (colons, spaces and case are
                              ignored). Defaults to $YFT_EXPECTED_CERT_SHA256 when that is set.
  --expected-version NAME     Required versionName.
  --previous-apk FILE         Earlier release: require the same package, the same signer
                              certificate and a higher versionCode (upgrade compatibility).
  --out-dir DIR               Copy the APK to DIR/video-downloader-<version>.apk and write
                              DIR/SHA256SUMS and DIR/release-info.txt.
  --allow-unsigned            Accept an unsigned APK (CI smoke check). Signature checks are
                              skipped and the result is reported as UNSIGNED, never as verified.
USAGE
  exit 2
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

ok() {
  echo "OK    $*"
}

expected_cert="${YFT_EXPECTED_CERT_SHA256:-}"
expected_version=""
previous_apk=""
out_dir=""
allow_unsigned=0
apk=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --expected-cert-sha256) [[ $# -ge 2 ]] || usage; expected_cert="$2"; shift 2 ;;
    --expected-version) [[ $# -ge 2 ]] || usage; expected_version="$2"; shift 2 ;;
    --previous-apk) [[ $# -ge 2 ]] || usage; previous_apk="$2"; shift 2 ;;
    --out-dir) [[ $# -ge 2 ]] || usage; out_dir="$2"; shift 2 ;;
    --allow-unsigned) allow_unsigned=1; shift ;;
    -h|--help) usage ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) [[ -z "$apk" ]] || usage; apk="$1"; shift ;;
  esac
done
[[ -n "$apk" ]] || usage
[[ -f "$apk" ]] || fail "APK not found: $apk"
[[ -z "$previous_apk" || -f "$previous_apk" ]] || fail "previous APK not found: $previous_apk"

# Newest installed build-tools directory that has the tool, or $ANDROID_BUILD_TOOLS.
build_tool() {
  local tool="$1" sdk dir
  if [[ -n "${ANDROID_BUILD_TOOLS:-}" && -x "$ANDROID_BUILD_TOOLS/$tool" ]]; then
    echo "$ANDROID_BUILD_TOOLS/$tool"
    return
  fi
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk" ]] || fail "set ANDROID_HOME (or ANDROID_BUILD_TOOLS) to find $tool"
  while IFS= read -r dir; do
    if [[ -x "$dir/$tool" ]]; then
      echo "$dir/$tool"
      return
    fi
  done < <(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -rV)
  fail "$tool not found under $sdk/build-tools"
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

normalize_hex() {
  printf '%s' "$1" | tr -d ': \t' | tr '[:upper:]' '[:lower:]'
}

colon_hex() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | sed 's/../&:/g; s/:$//'
}

aapt2="$(build_tool aapt2)"
apksigner="$(build_tool apksigner)"
zipalign="$(build_tool zipalign)"

badging_value() {
  # $1 = badging text, $2 = sed expression that prints the value.
  printf '%s\n' "$1" | sed -n "$2" | head -n 1
}

read_badging() {
  "$aapt2" dump badging "$1" 2>/dev/null || fail "aapt2 cannot read $1"
}

badging="$(read_badging "$apk")"
package_name="$(badging_value "$badging" "s/^package: name='\([^']*\)'.*/\1/p")"
version_code="$(badging_value "$badging" "s/^package: .* versionCode='\([^']*\)'.*/\1/p")"
version_name="$(badging_value "$badging" "s/^package: .* versionName='\([^']*\)'.*/\1/p")"
min_sdk="$(badging_value "$badging" "s/^sdkVersion:'\([^']*\)'.*/\1/p")"
target_sdk="$(badging_value "$badging" "s/^targetSdkVersion:'\([^']*\)'.*/\1/p")"
app_label="$(badging_value "$badging" "s/^application-label:'\([^']*\)'.*/\1/p")"

[[ "$package_name" == "com.alal.yft" ]] || fail "package is '$package_name', expected com.alal.yft"
ok "package $package_name ($app_label)"
[[ "$version_code" =~ ^[0-9]+$ ]] || fail "versionCode '$version_code' is not a number"
if [[ -n "$expected_version" && "$version_name" != "$expected_version" ]]; then
  fail "versionName is '$version_name', expected '$expected_version'"
fi
ok "version $version_name (versionCode $version_code), minSdk $min_sdk, targetSdk $target_sdk"
if printf '%s\n' "$badging" | grep -q '^application-debuggable'; then
  fail "APK is debuggable; build the release variant"
fi
ok "not debuggable"
"$zipalign" -c 4 "$apk" >/dev/null 2>&1 || fail "APK is not zip-aligned"
ok "zip-aligned"

# Prints "<schemes>|<signers>|<cert sha256>|<cert DN>" or fails.
signer_info() {
  local output schemes
  output="$("$apksigner" verify --verbose --print-certs "$1" 2>&1)" || return 1
  schemes="$(printf '%s\n' "$output" \
    | sed -n 's/^Verified using \(v[0-9.]*\) scheme.*: true$/\1/p' | paste -sd, -)"
  printf '%s|%s|%s|%s\n' \
    "$schemes" \
    "$(printf '%s\n' "$output" | sed -n 's/^Number of signers: //p' | head -n 1)" \
    "$(printf '%s\n' "$output" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)" \
    "$(printf '%s\n' "$output" | sed -n 's/^Signer #1 certificate DN: //p' | head -n 1)"
}

signature="UNSIGNED"
cert_sha256=""
if info="$(signer_info "$apk")"; then
  IFS='|' read -r schemes signers cert_sha256 cert_dn <<<"$info"
  [[ "$signers" == "1" ]] || fail "expected exactly one signer, found '${signers:-none}'"
  [[ ",$schemes," == *",v2,"* || ",$schemes," == *",v3,"* ]] \
    || fail "APK is not signed with APK Signature Scheme v2 or v3 (schemes: ${schemes:-none})"
  [[ -n "$cert_sha256" ]] || fail "could not read the signer certificate fingerprint"
  [[ "$cert_dn" != *"CN=Android Debug"* ]] || fail "APK is signed with the Android debug key"
  signature="signed ($schemes, 1 signer)"
  ok "signature verified: $schemes, 1 signer"
  ok "certificate SHA-256 $(colon_hex "$cert_sha256")"
  if [[ -n "$expected_cert" ]]; then
    [[ "$(normalize_hex "$cert_sha256")" == "$(normalize_hex "$expected_cert")" ]] \
      || fail "signer certificate does not match the expected release certificate"
    ok "certificate matches the expected release certificate"
  else
    echo "WARN  no expected certificate given; compare the fingerprint above with the owner's key"
  fi
elif [[ "$allow_unsigned" -eq 1 ]]; then
  [[ -z "$expected_cert" && -z "$previous_apk" ]] \
    || fail "--allow-unsigned cannot be combined with certificate or upgrade checks"
  echo "WARN  APK is unsigned (allowed by --allow-unsigned); it cannot be installed"
else
  fail "APK signature does not verify (unsigned or corrupt); see 'apksigner verify --verbose'"
fi

if [[ -n "$previous_apk" ]]; then
  previous_badging="$(read_badging "$previous_apk")"
  previous_package="$(badging_value "$previous_badging" "s/^package: name='\([^']*\)'.*/\1/p")"
  previous_code="$(badging_value "$previous_badging" \
    "s/^package: .* versionCode='\([^']*\)'.*/\1/p")"
  [[ "$previous_package" == "$package_name" ]] \
    || fail "previous APK package '$previous_package' differs from '$package_name'"
  [[ "$previous_code" =~ ^[0-9]+$ && "$version_code" -gt "$previous_code" ]] \
    || fail "versionCode $version_code is not higher than the previous $previous_code"
  previous_info="$(signer_info "$previous_apk")" || fail "previous APK signature does not verify"
  IFS='|' read -r _ _ previous_cert _ <<<"$previous_info"
  [[ "$(normalize_hex "$previous_cert")" == "$(normalize_hex "$cert_sha256")" ]] \
    || fail "previous APK has a different signer; Android would refuse the upgrade"
  ok "upgrade-compatible with versionCode $previous_code (same package and signer)"
fi

apk_sha256="$(sha256_of "$apk")"
apk_bytes="$(wc -c <"$apk" | tr -d ' ')"
ok "APK $apk_bytes bytes, SHA-256 $apk_sha256"

if [[ -n "$out_dir" ]]; then
  mkdir -p "$out_dir"
  staged="video-downloader-$version_name.apk"
  [[ "$signature" != "UNSIGNED" ]] || staged="video-downloader-$version_name-unsigned.apk"
  cp "$apk" "$out_dir/$staged"
  (cd "$out_dir" && printf '%s  %s\n' "$apk_sha256" "$staged" >SHA256SUMS)
  commit="unknown"
  if git rev-parse --verify HEAD >/dev/null 2>&1; then
    commit="$(git rev-parse HEAD)"
    [[ -z "$(git status --porcelain --untracked-files=no)" ]] || commit="$commit-dirty"
  fi
  {
    echo "app_label=$app_label"
    echo "package=$package_name"
    echo "version_name=$version_name"
    echo "version_code=$version_code"
    echo "min_sdk=$min_sdk"
    echo "target_sdk=$target_sdk"
    echo "apk_file=$staged"
    echo "apk_bytes=$apk_bytes"
    echo "apk_sha256=$apk_sha256"
    echo "signature=$signature"
    echo "certificate_sha256=${cert_sha256:+$(colon_hex "$cert_sha256")}"
    echo "source_commit=$commit"
    echo "generated_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >"$out_dir/release-info.txt"
  ok "staged $out_dir/$staged with SHA256SUMS and release-info.txt"
fi

if [[ "$signature" == "UNSIGNED" ]]; then
  echo "RESULT: UNSIGNED - metadata checks passed; this APK is not a release artifact"
else
  echo "RESULT: VERIFIED - $signature"
fi
