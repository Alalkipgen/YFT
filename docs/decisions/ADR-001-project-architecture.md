# ADR-001: Modular feature-first Android architecture

- Status: Accepted for Phase 1
- Date: 2026-09-30

## Context

YFT needs browser, extraction, preview and download systems that can evolve independently. Website adapters change more often than the transfer engine, while excessive Gradle modules create avoidable build overhead.

## Decision

Use nine production modules for app shell, shared data/model/browser/media/download infrastructure and extractor boundaries. Keep UI features as packages under `:app` initially. Use ViewModel/StateFlow unidirectional state and interface-based domain boundaries.

## Consequences

- Site breakage remains isolated.
- Generic media functionality is testable without a site adapter.
- Build complexity remains bounded.
- Phase 1 must enforce dependency direction and avoid duplicate frameworks.
