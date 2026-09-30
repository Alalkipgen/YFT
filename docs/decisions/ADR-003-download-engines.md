# ADR-003: Separate direct and adaptive-stream download engines

- Status: Accepted for Phase 4 planning
- Date: 2026-09-30

## Context

Direct byte-range files and HLS/DASH adaptive streams require different transfer behavior. Preview technology alone does not guarantee exportable files.

## Decision

Use a DownloadPlanner with explicit Direct, SegmentedDirect, Hls, Dash and AudioVideoMux plans. Adapt the tested direct-transfer concepts from AlalDownloader. Use Media3 for preview and manifest understanding, then implement export behavior deliberately. Do not add FFmpeg until a Phase 4 ADR evaluates license, size and alternatives.

## Consequences

- Direct downloads can mature independently.
- Separate tracks and muxing cannot be hidden behind a generic URL download.
- Unsupported combinations produce clear failures rather than corrupt output.
