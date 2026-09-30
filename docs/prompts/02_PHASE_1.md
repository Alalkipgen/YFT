CURRENT TASK: PHASE 1 — PRODUCTION FOUNDATION

Verify Phase 0 and its documentation first. Do not implement full detection or downloading.

OBJECTIVES
- Create the production module/package structure recorded in ARCHITECTURE.md.
- Add Compose/Material 3 app shell and navigation.
- Wire the selected DI, Room, DataStore, OkHttp and Media3 foundations.
- Configure debug/release builds and CI.

REQUIRED SCREENS
Home, Browser, Detected Media, Preview, Downloads, Library, Settings and About. They may be placeholders but must be real navigable Compose screens.

REQUIREMENTS
- ViewModel/StateFlow unidirectional state pattern
- light/dark theme foundation
- error/result model and secret-redacting logging
- database baseline and migration test
- no duplicate frameworks
- CI for lint/static checks, unit tests and debug build

DEFINITION OF DONE
- App launches to Home.
- All placeholder routes and back navigation work.
- Foundation dependencies are wired.
- CI exists; tests and debug build pass.
- Documentation/handoff updated.
- phase-1 commit created.

Do not start Phase 2.
