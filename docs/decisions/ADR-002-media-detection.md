# ADR-002: Layered generic media detection

- Status: Accepted for Phase 2
- Date: 2026-09-30

## Context

A URL-extension detector alone misses generated, signed, manifest and blob-backed media. Android WebView observes request URLs/headers but does not expose all response metadata passively.

## Decision

Combine DownloadListener, a read-only DOM probe, observed HTTP(S) request URLs, manifest recognition, bounded metadata probes and optional site-adapter hints. Normalize candidates per page and treat literal `blob:` URLs as pointers to underlying media rather than files.

## Consequences

- Better coverage without proxying every response.
- Bounded probes need cancellation, limits and cookie/header replay.
- Candidate metadata remains explicitly unknown until resolved.
