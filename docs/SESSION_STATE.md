# Session State

Update this file before every checkpoint push. Keep it short, factual and sufficient for a new chat to resume without guessing.

- Current phase: Phase 6 follow-up — UI redesign to the owner's images (`docs/design/DESIGN-NOTES.md`, `docs/design/reference/`). Phase 6 itself is complete at `4bdad07`. Phase 7 has NOT started and must not start until the owner asks
- Current branch: `work/phase-6-ui-redesign` (created from `4bdad07`; never merge to `main` without the owner)
- Redesign plan (one checkpoint per task): 0 Home re-check vs renders ✅ · 1 design system ✅ · 2 app icon + shell ✅ · 3 Home + Promptbox states + Your sites + Recent + link inspector ✅ · 4 Browser + Found on this page sheet ✅ · 5 Download as sheet (Preview) ✅ · 6 Downloads ✅ · 7 Library + mini player + thumbnails ✅ · 8 Settings + About · 9 dark/a11y pass, screenshot renders, docs, full validation
- Last completed task: Task 7 — Library to `05` (decision 13 in DESIGN-NOTES): header "Library" with search (a field under the title that matches names as you type) and sort (Newest first by default, Oldest first, Name, Largest first); outlined All / Video / Audio chips (Mint Soft when selected); two-column grid of 16:10 tiles with the file's own frame (a tenth of the way in, at most 10 s) or cover art, length badge, title, "720p · 96 MB" / "M4A · 7 MB" and a ⋯ menu (Play, Open with…, Share, Delete after a confirmation; the same choices as TalkBack actions). Details come from `RetrieverMediaDetailsSource` (MediaMetadataRetriever, two files at a time, memory LRU sized by bitmap bytes, nothing on disk). Tapping plays in the app (`ExoLibraryPlayback`): audio in the app-wide mini player above the bottom bar (Pause/Play, seek line with a thumb, X stops), video on the full-screen `player` route (system bars hidden, tap to pause, seek line, X or Back closes and stops); playback pauses on ON_STOP (not on rotation) and a failure says "<title> can't be played here. Try Open with… instead." Home Recent and finished Downloads use the same frames, lengths and picture sizes; Downloads Play now plays in the app, with Open with… in the card menu. New `YftSeekBar`, outlined `YftFilterChip`, beamed-notes audio glyph (`ic_music_notes_filled`).
- Work in progress: Task 8 (Settings + About to `06`) is HALF DONE and pushed as a `wip` commit (tests skipped, build not compiled yet). Done: `SettingsScreen.kt` rewritten (no back arrow; APPEARANCE / DOWNLOADS / PRIVACY / ABOUT cards; Theme row with compact `YftSegmentedControl` beside or below the label via `BesideOrBelow`; Save files to → choice dialog; Wi-Fi only + Ask before using mobile data switches (Ask disabled with its stored value and "Not needed while Wi-Fi only is on"); `YftStepper` for Downloads at the same time; Preferred quality → choice dialog; Clear rows open the existing confirmations; Version → About, Licenses → Licenses; footer "No ads · No tracking · No account"), `SettingsRoute(themeMode, onThemeModeChanged, onOpenAbout, onOpenLicenses)` and `SettingsScreen(..., modifier, versionName, onOpenAbout, onOpenLicenses)` (no `onNavigateBack` any more); `AboutScreen(onNavigateBack, onOpenLicenses, versionName, versionCode)` restyled into cards (licenses moved out). Components: `YftIconButton(iconSize)`, compact `YftStepper` pill drawn inside 48dp targets, `YftSegmentedControl(compact)`, `YftGroupLabel` 12sp with 12dp start.
- Build status: NOT RUN for the Task 8 wip commit (expected to fail to compile until the steps below are done). Last green: Task 7 `59b88fb` — app 318 tests, 0 failures, 11 render tests skipped without `YFT_RENDER_DIR`; lint 0 errors, 87 warnings.
- Known failure/blocker: no device/emulator (`/dev/kvm` unavailable). Build env (this sandbox): `source /data/yft-env.sh` — `JAVA_HOME=/data/.tools/jdk17`, `ANDROID_HOME=ANDROID_SDK_ROOT=/data/.android-sdk`, `GRADLE_USER_HOME=/data/.gradle-home`, `~/.m2 -> /data/.m2` (Robolectric jars); kill stale daemons with `pkill -f "[G]radleDaemon"`
- Next exact action: finish Task 8 — (1) add `feature/about/LicensesScreen.kt` (YftTopBar "Licenses" + back; Bundled code / Libraries groups from `OpenSourceNotices` with expandable license text; tags `license-<id>`, `license-text-<id>`, content tag `licenses-content`); (2) add `YftDestination.LICENSES("licenses", "Licenses", …)` and wire `YftNavHost`: `settingsContent: (onOpenAbout, onOpenLicenses)`, ABOUT gets `onOpenLicenses`, new LICENSES composable; (3) update tests: `SettingsScreenTest` (pickers via `settings-location` / `settings-quality` then `location-*` / `quality-*`; stepper `concurrency-increase`; Ask row disabled + hint), `AboutScreenTest` (no bullets; group labels are uppercase text with normal-case content descriptions), new `LicensesScreenTest`, `YftDestinationTest` (+Licenses), `YftNavigationSmokeTest` (settingsContent lambda, Version → About, Licenses); (4) renders `06-settings` + `06-settings-dark` in `DesignRenderTest`, compare with `06`; (5) decision 14 in DESIGN-NOTES; (6) full run + checkpoint. Then Task 9.
- Last pushed checkpoint: `59b88fb` — Task 7 (green); this Task 8 `wip` commit follows
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
