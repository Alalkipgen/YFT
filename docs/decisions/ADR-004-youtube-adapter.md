# ADR-004: Ship no YouTube adapter

- Status: Superseded by [ADR-005](ADR-005-youtube-owner-override.md) on 2026-10-02 (owner decision)
- Date: 2026-10-02

## Context

Phase 5 ordered YouTube last and required either an isolated adapter or an honest blocker report.
Unlike TikTok, Facebook and Vimeo, YouTube does not expose a playable URL on the page the user's
browser already loaded: stream URLs depend on transforms carried in the site's own player
JavaScript, on per-session proof-of-origin tokens, and increasingly on a server-driven streaming
protocol instead of fetchable files. The full analysis, with sources, is in
[YOUTUBE_RISK_REVIEW.md](../YOUTUBE_RISK_REVIEW.md).

## Decision

Do not implement a YouTube adapter. Keep YouTube links on the generic detector path, claim no
YouTube support anywhere in the product, and record the YouTube support-matrix row as Blocked.
Reopening requires extraction without remote code execution or client impersonation,
fixture-pinnable behavior, and a maintainer accepting the cadence and distribution exposure in a
new ADR.

## Consequences

- The Phase 5 rule against unsigned remote executable extractor code stays intact, and no adapter
  ships whose offline fixtures cannot detect its own breakage.
- `ShippedAdaptersTest` pins the shipped adapter set and the unclaimed YouTube hosts, so the
  decision cannot be reversed silently.
- Users who expect YouTube downloads get the generic pipeline's honest result instead of a
  support claim the project cannot keep.
