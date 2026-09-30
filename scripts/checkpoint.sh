#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo 'Usage: bash scripts/checkpoint.sh "short checkpoint description"' >&2
  exit 2
}

[[ $# -ge 1 ]] || usage
message="$*"
root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Not inside a Git repository." >&2
  exit 1
}
cd "$root"

branch="$(git branch --show-current)"
case "$branch" in
  work/phase-*) ;;
  *)
    echo "Checkpoint refused: current branch '$branch' is not work/phase-* ." >&2
    echo "Create or switch to a phase branch before checkpointing." >&2
    exit 1
    ;;
esac

git remote get-url origin >/dev/null 2>&1 || {
  echo "Checkpoint refused: remote 'origin' is not configured." >&2
  exit 1
}

[[ -f docs/SESSION_STATE.md ]] || {
  echo "Checkpoint refused: docs/SESSION_STATE.md is missing." >&2
  exit 1
}

git add -A

if git diff --cached --quiet; then
  echo "No staged changes to checkpoint."
  exit 0
fi

forbidden=0
while IFS= read -r path; do
  base="$(basename "$path")"
  case "$base" in
    .env.example) ;;
    *.jks|*.keystore|*.p12|*.pfx|*.pem|*.key|local.properties|.env|.env.*|secrets.properties|signing.properties|keystore.properties|credentials.json)
      echo "Sensitive file refused: $path" >&2
      forbidden=1
      ;;
  esac
done < <(git diff --cached --name-only --diff-filter=ACMR)
[[ "$forbidden" -eq 0 ]] || exit 1

if ! git diff --cached --name-only | grep -qx 'docs/SESSION_STATE.md'; then
  echo "Checkpoint refused: update docs/SESSION_STATE.md for this checkpoint." >&2
  exit 1
fi

git diff --cached --check

added_lines="$(git diff --cached --no-ext-diff --unified=0 --no-color | sed -n 's/^+\([^+]\)/\1/p')"
if printf '%s\n' "$added_lines" | grep -Eiq -- '-----BEGIN (RSA |EC |OPENSSH |)?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_-]{30,}|AKIA[0-9A-Z]{16}'; then
  echo "Checkpoint refused: staged changes contain a likely secret." >&2
  exit 1
fi

prefix="checkpoint"
if [[ "${CHECKPOINT_SKIP_TESTS:-0}" == "1" ]]; then
  prefix="wip"
  echo "WARNING: tests skipped; the failure must be documented in SESSION_STATE.md." >&2
elif [[ -n "${CHECKPOINT_TEST_COMMAND:-}" ]]; then
  bash -lc "$CHECKPOINT_TEST_COMMAND"
elif [[ -x ./gradlew ]]; then
  ./gradlew --no-daemon testDebugUnitTest
elif command -v gradle >/dev/null 2>&1 && [[ -f spikes/phase0-media/settings.gradle.kts ]]; then
  gradle -p spikes/phase0-media --no-daemon testDebugUnitTest
else
  echo "WARNING: no automatic quick-test runner found; record manual validation in SESSION_STATE.md." >&2
fi

scope="${branch#work/}"
git commit -m "$prefix($scope): $message"
git push -u origin HEAD

echo "Remote checkpoint created: $(git rev-parse --short HEAD) on $branch"
