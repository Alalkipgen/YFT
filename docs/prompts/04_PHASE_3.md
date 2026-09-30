CURRENT TASK: PHASE 3 — PREVIEW AND VARIANT RESOLUTION

Verify Phase 2 candidate detection first.

OBJECTIVES
- Validate candidates and resolve structured assets/variants.
- Preview supported non-DRM media with Media3 using required browser request context.
- Show honest quality, format, codec, audio and estimated-size data.
- Reject unsupported/DRM streams clearly.

IMPLEMENT
- redirects and MIME/range probing
- HLS/DASH manifest parsing
- resolution, FPS, codec, bitrate and duration extraction when available
- video/audio/separate-track classification
- size estimation marked as estimated
- expiry and DRM detection
- Media3 preview screen and error states
- video/audio tabs and variant selector

Never fetch the entire file only to estimate metadata. Never show invented values.

TESTS
Direct MP4, multi-variant HLS/DASH, separate tracks, unknown size, expired URL, cookie-protected preview, unsupported codec, DRM and malformed manifests.

DEFINITION OF DONE
- Direct/HLS/DASH fixtures preview where supported.
- Real variants display; unknown values remain unknown.
- DRM/unsupported media fails clearly.
- Tests/debug build pass; docs/handoff updated; phase-3 commit created.

Do not start Phase 4.
