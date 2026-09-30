# YFT Agent Operating Rules

These rules apply to every coding agent and every chat working on this repository.

## Source of truth

Do not rely on chat history. At the beginning of a session:

1. Inspect `git status`, the current branch and recent commits.
2. Read:
   - `docs/PROJECT_CONTEXT.md`
   - `docs/ARCHITECTURE.md`
   - `docs/PHASE_STATUS.md`
   - `docs/HANDOFF.md`
   - `docs/SESSION_STATE.md`
3. Verify the existing build before editing.
4. Work only on the current phase. Never start the next phase automatically.

If documentation and verified code disagree, build/test results take priority and the documentation must be corrected.

## Branch policy

- Never develop directly on `main`.
- Use a branch named `work/phase-N-short-description`, for example:
  - `work/phase-1-foundation`
  - `work/phase-2-browser-detection`
- Checkpoint pushes to `work/phase-*` branches are pre-authorized.
- Merging or pushing phase-completion changes to `main` requires a green full validation and explicit user approval.
- Publishing releases always requires explicit user approval.

## Mandatory checkpoint protocol

Do not wait until the end of a phase or until context is nearly exhausted. Create a remote checkpoint:

- after each meaningful, independently describable milestone;
- after roughly 20–30 minutes of active implementation;
- before a long build, migration or risky refactor;
- before asking a blocking question;
- before ending a response/session.

For every checkpoint:

1. Update `docs/SESSION_STATE.md` with the exact current state and next action.
2. Run the relevant quick validation.
3. Execute:

```bash
bash scripts/checkpoint.sh "short description"
```

The script stages changes, rejects sensitive files and obvious secrets, runs validation, commits and pushes the current `work/phase-*` branch.

If valuable work is temporarily broken and context loss is a greater risk, record the exact failure in `SESSION_STATE.md`, then use:

```bash
CHECKPOINT_SKIP_TESTS=1 bash scripts/checkpoint.sh "wip: exact unfinished work"
```

This exception is allowed only on a `work/phase-*` branch. Never push knowingly broken work to `main`.

A local commit or `git stash` is not a remote backup. If a checkpoint push fails, stop and report it; do not continue accumulating unpushed work.

## Phase completion

Before declaring a phase complete:

1. Run the full phase validation.
2. Update `PHASE_STATUS.md`, `HANDOFF.md`, `SESSION_STATE.md` and other affected docs.
3. Create and push a clean phase-completion commit on the work branch.
4. Report exact commands, results, commit SHA, branch and known limitations.
5. Do not merge to `main` or start the next phase without approval.

## Security

Never commit or print:

- passwords or tokens;
- cookies or signed media URLs;
- `.jks`/`.keystore` files;
- private keys/certificates;
- `local.properties`, `.env`, signing or secrets property files.

Never disable TLS verification. Never fabricate test results or hide a failing build.
