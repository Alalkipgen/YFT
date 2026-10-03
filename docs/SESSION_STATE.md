# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 6 follow-up — UI redesign to the owner's images (`docs/design/DESIGN-NOTES.md`, `docs/design/reference/`). Phase 6 itself is complete at `4bdad07`. Phase 7 has NOT started and must not start until the owner asks
- Current branch: `work/phase-6-ui-redesign` (created from `4bdad07`; never merge to `main` without the owner)
- Redesign plan (one checkpoint per task): 0 Home re-check vs renders ✅ · 1 design system ✅ · 2 app icon + shell ✅ · 3 Home + Promptbox states + Your sites + Recent + link inspector ✅ · 4 Browser + Found on this page sheet ✅ · 5 Download as sheet (Preview) ✅ · 6 Downloads ✅ · 7 Library + mini player + thumbnails ✅ · 8 Settings + About · 9 dark/a11y pass, screenshot renders, docs, full validation
- Last completed task: Task 7 — Library to `05` (decision 13 in DESIGN-NOTES): header "Library" with search (a field under the title that matches names as you type) and sort (Newest first by default, Oldest first, Name, Largest first); outlined All / Video / Audio chips (Mint Soft when selected); two-column grid of 16:10 tiles with the file's own frame (a tenth of the way in, at most 10 s) or cover art, length badge, title, "720p · 96 MB" / "M4A · 7 MB" and a ⋯ menu (Play, Open with…, Share, Delete after a confirmation; the same choices as TalkBack actions). Details come from `RetrieverMediaDetailsSource` (MediaMetadataRetriever, two files at a time, memory LRU sized by bitmap bytes, nothing on disk). Tapping plays in the app (`ExoLibraryPlayback`): audio in the app-wide mini player above the bottom bar (Pause/Play, seek line with a thumb, X stops), video on the full-screen `player` route (system bars hidden, tap to pause, seek line, X or Back closes and stops); playback pauses on ON_STOP (not on rotation) and a failure says "<title> can't be played here. Try Open with… instead." Home Recent and finished Downloads use the same frames, lengths and picture sizes; Downloads Play now plays in the app, with Open with… in the card menu. New `YftSeekBar`, outlined `YftFilterChip`, beamed-notes audio glyph (`ic_music_notes_filled`).
- Work in progress: none uncommitted. Owner said go: continue Task 8 → 9 without pausing, one checkpoint per task.
- Build status: PASS (Task 7) — `./gradlew --no-daemon -q --continue :app:testDebugUnitTest :app:lintDebug`: app 318 tests, 0 failures (11 render tests run only with `YFT_RENDER_DIR`, otherwise skipped); lint 0 errors, 87 warnings (57 GradleDependency, 24 VectorPath, 6 AGP version). Other modules unchanged since `9fa41c2` (not re-run). Renders: `YFT_RENDER_DIR=/data/renders ./gradlew --no-daemon -q :app:testDebugUnitTest --rerun --tests "com.alal.yft.design.DesignRenderTest"`. Robolectric note: legacy graphics cannot hit-test shapes with only some rounded corners (sheet tops, mini player), so tests that tap inside them use `@GraphicsMode(NATIVE)`; text-overflow tests and real bitmaps also need NATIVE. Lint: `produceState` must assign in its lambda, so `rememberMediaDetails` uses `remember` + `LaunchedEffect`.
- Known failure/blocker: no device/emulator (`/dev/kvm` unavailable). Build env (this sandbox): `source /data/yft-env.sh` — `JAVA_HOME=/data/.tools/jdk17`, `ANDROID_HOME=ANDROID_SDK_ROOT=/data/.android-sdk`, `GRADLE_USER_HOME=/data/.gradle-home`, `~/.m2 -> /data/.m2` (Robolectric jars); kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: Task 8 — Settings + About to `06`: no back arrow on the Settings tab; APPEARANCE / DOWNLOADS / PRIVACY / ABOUT group labels over bordered cards with leading icons and dividers; Theme with System / Light / Dark segments; Save files to → "Download/YFT" ›; Download over Wi-Fi only and Ask before using mobile data switches; Downloads at the same time − 3 + stepper; Preferred quality → "Highest available" ›; Clear browsing data ›, Clear download history › (confirmations kept); Version "1.0.0-beta.1"; Licenses › (About); footer "No ads · No tracking · No account" with the shield.
- Last pushed checkpoint: `4a64606` — Task 6; this Task 7 checkpoint follows
- Last updated: 2026-10-03

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
