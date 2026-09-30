CURRENT TASK: PHASE 5 — WEBSITE-SPECIFIC EXTRACTOR ADAPTERS

Verify the generic detector, preview and download pipeline first.

OBJECTIVES
- Finalize extractor API/registry.
- Isolate every website adapter.
- Add sites one at a time with fixtures, feature flags and structured failures.
- Preserve generic fallback and avoid DRM/access-control bypass.

ORDER
5A TikTok
5B Facebook
5C other justified public sites
5D YouTube last as a separately documented adapter

Each adapter should support URL matching, canonical page identity, metadata/variants, required request context, expiry and re-resolution, and structured errors.

RULES
- No site parsing in browser UI or download engine.
- No fabricated or wrong media fallback.
- No unsigned remote executable extractor code.
- Verify third-party licenses.
- Do not log session secrets.
- Test public/authorized non-DRM content only.
- For YouTube, document maintenance/distribution constraints and report a blocker honestly if reliable implementation is not feasible.

TESTS PER SITE
Standard/short URLs, multiple qualities, separate tracks, expired link, no media, deleted/private/login-required, response changes, generic fallback and structured errors.

Update SUPPORT_MATRIX with patterns, media types, limits, auth requirements and verification date.

DEFINITION OF DONE
- Registry and generic fallback work.
- Every claimed site has real fixtures/tests.
- Broken pages fail clearly and independently.
- Tests/debug build pass; docs/handoff updated.
- Use reviewable adapter commits and a phase-5 completion commit.

Do not start Phase 6.
