#!/usr/bin/env bash
# Public HTTPS page check. Only safe summary fields reach stdout/stderr.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/scripts/live-check.py" "$@"
