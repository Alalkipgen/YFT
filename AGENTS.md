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
   - `docs/FIX_ADD_PLAN.md` (Phase 11: status board, decisions, findings and tasks)
3. Verify the existing build before editing.
4. Follow the task order in `docs/FIX_ADD_PLAN.md` §1. Owner instruction (2026-10-04): continue
   task after task P1 → P7 without asking; stop only for a failure you cannot fix. P8 (signed
   release) waits for the owner's phone test of the P7 preview APK.

## Task workflow (Phase 11)

- Do the `docs/FIX_ADD_PLAN.md` tasks one at a time, each following its prompt in `docs/prompts/`
  (`00_NEXT_TASK.md` picks the next one). Update the status board in every task checkpoint.
- A task that needs an owner decision still `PENDING` in FIX_ADD_PLAN §3 is blocked: ask the
  owner in Burmese and stop instead of guessing.
- Site tasks need a live check of a public page (`scripts/live-check.sh`). Report
  status, host, path and markers only, never bodies, cookies or signed URLs.
- Final reports to the owner are written in Burmese (FIX_ADD_PLAN §0.5, short). The app text stays English.
- Product rules: [ADR-006](docs/decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique
  for public videos; no DRM, paid, private-content or age-gate bypass; adapters never sign in.
- Anything outside the task goes to FIX_ADD_PLAN §7 Backlog.

## Resuming work

A pushed branch is the only durable handoff; a local commit or a stash is not. A new chat runs:

```bash
git fetch --all --prune
git status
git branch --show-current
git log -5 --oneline
```

Then it reads the files above, checks out the branch recorded in `docs/SESSION_STATE.md`, pulls
it and runs the recorded validation before editing. A `wip` checkpoint must be repaired before
its task is marked done.

If documentation and verified code disagree, build/test results take priority and the documentation must be corrected.

## Branch policy

- Never develop directly on `main`.
- Use a branch named `work/phase-N-short-description`, for example:
  - `work/phase-1-foundation`
  - `work/phase-2-browser-detection`
- Checkpoint pushes to `work/phase-*` branches are pre-authorized.
- Merging or pushing phase-completion changes to `main` requires a green full validation and explicit user approval. For Phase 11 nothing is merged before P8, and P8 runs only after the owner approves the P7 preview APK (`docs/FIX_ADD_PLAN.md` §3).
- Publishing releases always requires explicit user approval. The owner approved the signed `1.0.0-beta.4` draft after his P7 phone test (2026-10-04, P8).

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
