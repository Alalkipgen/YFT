#!/usr/bin/env bash
# R6 / YT-2 canary (owner-run, never in CI): asks YouTube live, as Master's module would, whether
# visionOS alone still answers a public video with direct addresses. Exit 0 = pass.
# A client-table value changes only after this fails.
# usage: bash scripts/master-youtube-canary.sh <public video id>
set -euo pipefail
id="${1:?usage: $0 <public video id>}"
cd "$(dirname "$0")/.."
./gradlew -q :extractor-master:test \
  --tests 'com.alal.yft.extractor.master.modules.youtube.MasterYouTubeCanaryTest' \
  -Pyft.youtubeCanary="$id" -i | grep 'canary:' || true
xml=extractor-master/build/test-results/test/TEST-com.alal.yft.extractor.master.modules.youtube.MasterYouTubeCanaryTest.xml
if grep -q '<failure' "$xml"; then echo "canary: FAIL"; exit 1; fi
if grep -q '<skipped' "$xml"; then echo "canary: SKIPPED"; exit 2; fi
echo "canary: PASS"
