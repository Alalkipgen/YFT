CURRENT TASK: PHASE 4 — DOWNLOAD ENGINES AND RECOVERY

Verify Phase 3 resolution/preview before implementing transfers.

OBJECTIVES
- Reliable direct, non-DRM HLS and DASH downloads.
- Separate audio/video handling where supported.
- Foreground execution, queueing, persistence and recovery.
- Safe MediaStore/SAF export.

PLANS
Direct, SegmentedDirect, Hls, Dash and AudioVideoMux. Adapt suitable tested concepts from AlalDownloader into YFT interfaces.

DIRECT REQUIREMENTS
Range detection, segmentation, no-range fallback, headers/cookies/referer, pause/resume/retry/cancel, temp parts, integrity/final-size checks, redirect/expiry handling, safe filenames.

STREAM REQUIREMENTS
Selected-track segment download, temporary cleanup, clear DRM rejection, resolver callback for expired URLs and explicit mux compatibility/failure.

BACKGROUND/STORAGE
Foreground service, persistent notification, Room task state, process restart and network recovery, controlled concurrency, MediaStore/SAF output and low-storage handling. WorkManager is not the sole large-transfer engine.

TESTS
Range/no-range, pause/resume, process restart, network switching, duplicates, expired URL, low storage, corruption, HLS, DASH, separate tracks, cancellation, cleanup and concurrency.

DEFINITION OF DONE
- Direct and at least one HLS/DASH fixture complete correctly.
- Recovery is tested; incomplete files never become completed.
- Output appears in the expected storage location.
- Tests/debug build pass; docs/handoff updated; phase-4 commit created.

Do not start Phase 5.
