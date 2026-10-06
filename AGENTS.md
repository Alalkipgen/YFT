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
   - `docs/FIX_ADD_PLAN.md` (Phase 13: status board, decisions, findings, tasks and §0.7 file
     ownership for parallel agents)
3. Verify the existing build before editing.
4. Phase 13 runs three agents at once (`docs/FIX_ADD_PLAN.md` §0.7): Agent A P27, Agent B
   P28 → P29, Agent C P30 → P31 → P32, each on its own branch, folder and files; then P33 (Agent
   A merges A → B → C, Preview #5) and P8 (signed release) with the owner's OK. Do only your agent's
   tasks, task after task without asking; stop at `READY FOR MERGE`, for a failure you cannot
   fix, or when the owner says stop.

## Task workflow (Phase 13)

- Do your agent's `docs/FIX_ADD_PLAN.md` tasks one at a time, following its prompt in
  `docs/prompts/` (`A-merge-speed.md`, `B-generic-main.md`, `C-browser.md`;
  `M-merge-preview5.md` for P33). Change only the files §0.7 gives your agent; in shared docs
  (`docs/SESSION_STATE.md`, `CHANGELOG.md`, `docs/TEST_MATRIX.md`) edit only your own section.
  Agents A, B and C record status in their SESSION_STATE section; only P33 edits
  `docs/FIX_ADD_PLAN.md` and `docs/prompts/`.
- A change in another agent's files is a hand-off: write it in your SESSION_STATE section and
  your report instead of making it.
- A task that needs an owner decision still `PENDING` in FIX_ADD_PLAN §3 is blocked: ask the
  owner in Burmese and stop instead of guessing.
- Site tasks need a live check of a public page (`scripts/live-check.sh`). Report
  status, host, path and markers only, never bodies, cookies or signed URLs.
- Final reports to the owner are written in Burmese (FIX_ADD_PLAN §0.5, short). The app text stays English.
- Product rules: [ADR-006](docs/decisions/ADR-006-owner-override-any-working-method.md) (owner, 2026-10-03) — any working technique
  for public videos; no DRM, paid, private-content or age-gate bypass; adapters never sign in.
- Anything outside the task goes to your SESSION_STATE section as a backlog note (P33 moves it
  to FIX_ADD_PLAN §7 Backlog).

## Resuming work

A pushed branch is the only durable handoff; a local commit or a stash is not. A new chat runs:

```bash
git fetch --all --prune
git status
git branch --show-current
git log -5 --oneline
```

Then it reads the files above, checks out its agent's branch recorded in `docs/SESSION_STATE.md`,
pulls it and runs the recorded validation before editing. A `wip` checkpoint must be repaired before
its task is marked done.

If documentation and verified code disagree, build/test results take priority and the documentation must be corrected.

## Branch policy

- Never develop directly on `main`.
- Use a branch named `work/phase-N-short-description`, for example:
  - `work/phase-1-foundation`
  - `work/phase-2-browser-detection`
- Checkpoint pushes to `work/phase-*` branches are pre-authorized.
- Merging or pushing phase-completion changes to `main` requires a green full validation and explicit user approval. In Phase 13 agents never push to `main`; P33 may fast-forward it only with the owner's `MAIN=OK`, and P8 runs only after the owner approves Preview #5 (`docs/FIX_ADD_PLAN.md` §5 P33, P8).
- Publishing releases always requires explicit user approval. The signed `1.0.0-beta.4` draft (P8) waits for the owner's OK after his phone test of Preview #5.

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
