# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 5E — make the YouTube adapter work (IN PROGRESS). Phase 5 itself is complete at `d7efebe`; Phase 6 has not started
- Current branch: `work/phase-5e-youtube` (created from `d7efebe`)
- Last completed task: Rebuilt the YouTube adapter core after the earlier local-only commit `9796ae1` was lost with its sandbox. `:extractor-api` gained `SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED`, `ExtractorHttpClient.postJson` and the `PlayerScriptRunner` boundary; `:extractor-sites` gained `youtube/` (URL matching, client profiles, player-response parser, extractor); `OkHttpExtractorClient` implements `postJson`
- Work in progress: test doubles, offline YouTube fixtures and tests, adapter registration behind a default-on flag, the WebView player-script runner, ADR-005 and the documentation updates. The adapter is not registered in the app yet
- Build status: not yet validated for this checkpoint; last full PASS is Phase 5 at `d7efebe` (293 tests, 0 failures)
- Known failure/blocker: The owner overrode ADR-004 and wants YouTube downloads (GitHub-only distribution); ADR-005 will record it. Plain page fetches from datacenter networks get `LOGIN_REQUIRED` ("confirm you're not a bot") with no `streamingData`; modern responses protect URLs with `signatureCipher` and the `n` parameter, so a player-script runner is needed on device. Adapter candidates have no track-type field, so only progressive (audio+video) YouTube streams are offered. No device/emulator (`/dev/kvm` unavailable). Build environment in this sandbox: `export JAVA_HOME=/data/toolchains/jdk17 ANDROID_HOME=/data/android-sdk ANDROID_SDK_ROOT=/data/android-sdk`; run Gradle with network; kill stale daemons before a full run (4 GB RAM)
- Next exact action: add `postJson` to `FakeExtractorHttpClient`, add `FakePlayerScriptRunner`, write the YouTube fixtures and tests, then register the adapter
- Last pushed checkpoint: `d7efebe` — Phase 5 completion; this checkpoint follows
- Last updated: 2026-10-02

## Checkpoint note template

```text
Current phase:
Current branch:
Last completed task:
Work in progress:
Build status and exact command:
Known failure/blocker:
Next exact action:
Last pushed checkpoint:
Last updated:
```
