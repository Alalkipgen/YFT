# Handoff: WebM merge fix, h-only, visionOS 1.04

Status: backlog (Phase 1.4). Plan only; no code written yet.

Rules for the next agent:
- All work on `spike/master-extractor-backup`. No separate PR to main (single tester).
- Read `MASTER_KEY_PHASE1_3_PLAN.md` (guardrails) and `PROGRESS.md` first.
- Stop and report if any parity test changes.
- A separate reviewer agent checks each item against its Done line.

Order: 1 and 3, then 2, then rebuild the `[d7]` APKs.

## 1. WebM merge bug (same code on main and spike)

Evidence (phone, Android 16 / API 36, video: Rick Astley - Never Gonna Give You Up 4K Remaster):
- 1440p WebM (VP9 + Opus) fails at MERGE on main `1.0.0-beta.3-preview.78` and on the D7 APK, 3 of 3 tries.
- Error: `INCOMPATIBLE_TRACKS`, stage MERGE, detail `IllegalArgumentException` with no message; merge ~2.2-2.4 s.
- 720p MP4 (avc1 + AAC) works, with sound. MP4 uses the stream-copy path; WebM uses the MediaMuxer path.

First step: add logs to find where it throws (`addTrack` video/audio, `readSampleData`, `writeSampleData`) and the largest sample size per track.

Suspects:
- A: `MediaMuxerAudioVideoMuxer` (core-download `AudioVideoMuxEngine.kt`) uses a 256 KiB buffer when `KEY_MAX_INPUT_SIZE` is missing; a 2K/4K VP9 keyframe is bigger. Fix: grow the buffer to fit the sample (cap 32 MiB).
- B: the WebM muxer rejects the Opus track format. Fix per the logs.

Test: a fixture that reproduces the failure (e.g. a VP9 keyframe > 256 KiB).

Done: CI green; 1440p and 2160p WebM merge on the phone, with sound.

## 2. h-only APK really runs H

Problem: in the `h-only` D7 mode the normal YouTube path still succeeds, so the list shows MP4/WebM rows and H never runs.

Fix: in `h-only` mode only, skip the normal path so H runs.

Done: the phone list shows HLS rows only; a test proves the normal path is skipped in that mode and nowhere else.

## 3. Bundle visionOS 1.04 as the alternate client

- Source: NewPipe, 2026-08-06.
- A check: 2026-10-16 15:41 UTC, 1.04 DIRECT, V pass on avc1 144p; same shape as 1.02.
- R0 stays 1.02. Guardrail 5 (versions never invented; at most 2 public bundled alternates).

Done: tests green; parity unchanged; A falls back to 1.04 when 1.02 is rejected.
