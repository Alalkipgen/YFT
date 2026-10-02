#!/usr/bin/env bash
# Refreshes the bundled yt-dlp ejs solver from its published PyPI wheel.
#
# Usage: scripts/update-youtube-solver.sh [VERSION SHA256]
#
# The wheel's SHA-256 must match the pinned value (or the one passed in), so an update is always a
# deliberate, reviewable change. The two solver files are copied unmodified; run
# scripts/verify-youtube-solver.mjs afterwards and update docs/THIRD_PARTY_NOTICES.md.
set -euo pipefail

VERSION="${1:-0.8.0}"
EXPECTED_SHA256="${2:-79300e5fca7f937a1eeede11f0456862c1b41107ce1d726871e0207424f4bdb4}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/youtube-solver"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

WHEEL_URL="$(
  curl -fsSL "https://pypi.org/pypi/yt-dlp-ejs/${VERSION}/json" | python3 -c '
import json, sys
release = json.load(sys.stdin)
wheels = [f for f in release["urls"] if f["filename"].endswith("-py3-none-any.whl")]
if len(wheels) != 1:
    sys.exit("expected exactly one universal wheel")
print(wheels[0]["url"])
'
)"
curl -fsSL -o "$WORK/solver.whl" "$WHEEL_URL"

ACTUAL_SHA256="$(sha256sum "$WORK/solver.whl" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "SHA-256 mismatch for yt-dlp-ejs ${VERSION}: got ${ACTUAL_SHA256}" >&2
  exit 1
fi

python3 - "$WORK/solver.whl" "$DEST" <<'PY'
import sys, zipfile
wheel, dest = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(wheel) as archive:
    for source, target in (("core.min.js", "yt.solver.core.min.js"), ("lib.min.js", "yt.solver.lib.min.js")):
        data = archive.read(f"yt_dlp_ejs/yt/solver/{source}")
        with open(f"{dest}/{target}", "wb") as out:
            out.write(data)
PY

echo "Bundled yt-dlp-ejs ${VERSION} (wheel ${ACTUAL_SHA256}):"
(cd "$DEST" && sha256sum yt.solver.core.min.js yt.solver.lib.min.js)
echo "Next: node scripts/verify-youtube-solver.mjs, then update docs/THIRD_PARTY_NOTICES.md."
