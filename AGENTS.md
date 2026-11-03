# YFT agent rules

These rules apply to every coding agent and every chat that works on this repository.

## Start of a session

Do not rely on chat history. A pushed branch is the only handoff; a local commit or a stash is not.

1. Look at the repository:

   ```bash
   git fetch --all --prune
   git status
   git branch --show-current
   git log -5 --oneline
   ```

2. Read:
   - `docs/SESSION_STATE.md`: where the work stands and the next step;
   - `docs/PLAN.md`: open release steps, owner decisions and known issues;
   - `docs/ARCHITECTURE.md`: modules and data flow;
   - `docs/GLOSSARY.md`: feature names;
   - `docs/TESTING.md`: commands and runbooks.
3. Check out the branch named in `docs/SESSION_STATE.md`, pull it, and run its recorded validation
   before you edit.

If the docs and verified code disagree, build and test results win, and the docs must be fixed.

## Product rules

- Public videos must download. Any working method is allowed: app or device client identities,
  proof-of-origin tokens, bot-check workarounds and the user's own browser session
  (`docs/DECISIONS.md`).
- No DRM, paid, private-content or age-check bypass. Adapters never sign in.
- A `blob:` address is never a file.
- App text is in English.

## How to work

- Do one task at a time. Change only the files the task needs.
- A task that needs an owner decision still open in `docs/PLAN.md` is blocked: ask the owner and
  stop instead of guessing.
- Site work needs a live check of a public page (`bash scripts/live-check.sh <url>`). Report only
  the status, host, path and markers, never bodies, cookies or signed URLs.
- Anything outside the task goes to the backlog in `docs/PLAN.md`.
- Final reports to the owner are short and in Burmese.

## Docs and comments

- Write plain English in short sentences.
- Use the feature names in `docs/GLOSSARY.md`. Do not add work labels (task, plan, run or agent
  names and numbers) to docs, code comments, commit messages or Lookup details. Literal app output
  in backticks, build flags, class names, commit SHAs, version numbers and released `CHANGELOG.md`
  sections stay as they are.
- A new feature gets its name in `docs/GLOSSARY.md` first, in the same commit as its code. Use that
  name in plans, comments and commit messages; never a new step or ID label.
- Docs describe the current state only. History lives in git and in released changelog sections.
- New owner decisions go to `docs/DECISIONS.md`, open questions to `docs/PLAN.md`, user-visible
  changes to the unreleased section of `CHANGELOG.md`.
- Never leave an empty file. Delete a file you would empty.

## Branches

- Never develop on `main`. Work on a `work/phase-<name>` branch (now `work/phase-v102-easy`).
- Pushes to `work/phase-*` branches are allowed. Never force-push.
- Merging to `main` needs green CI and the owner's approval: a pull request, merged with a merge
  commit.
- Publishing a release always needs the owner's approval ([docs/RELEASE.md](docs/RELEASE.md)).

## Checkpoints

Push a remote checkpoint:

- after each milestone you can describe on its own;
- after about 20–30 minutes of work;
- before a long build, a migration or a risky refactor;
- before you ask a blocking question;
- before you end a response or a session.

For each checkpoint:

1. Update `docs/SESSION_STATE.md` with the exact state and the next step.
2. Run the quick validation that fits the change.
3. Run `bash scripts/checkpoint.sh "short description"`. It refuses branches other than
   `work/phase-*`, rejects sensitive files and likely secrets, runs validation, commits and pushes.

If valuable work is broken and losing it is the bigger risk, write the exact failure in
`docs/SESSION_STATE.md`, then run
`CHECKPOINT_SKIP_TESTS=1 bash scripts/checkpoint.sh "wip: exact unfinished work"`. A `wip`
checkpoint must be repaired before its task counts as done.

If a push fails, stop and report it. Do not pile up unpushed work.

## CI

- A push that changes only docs (files under `docs/` or ending in `.md`) starts no checkpoint
  check, and a commit message with `[skip ci]` starts no workflow. The newest code commit carries
  the CI result.
- When a fresh result is needed, run "Work-branch checkpoint validation" by hand (Actions → Run
  workflow).

## Finishing a task

1. Run the full validation ([docs/TESTING.md](docs/TESTING.md)).
2. Update `docs/SESSION_STATE.md`, `CHANGELOG.md` and every affected doc.
3. Push a clean commit on the work branch.
4. Report the exact commands, results, commit SHA, branch and known limits.
5. Do not merge to `main` or start the next release without approval.

## Security

Never commit or print:

- passwords or tokens;
- cookies or signed media URLs;
- `.jks`/`.keystore` files;
- private keys/certificates;
- `local.properties`, `.env`, signing or secrets property files.

Never disable TLS verification. Never fabricate test results or hide a failing build.
