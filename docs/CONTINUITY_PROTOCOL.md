# Continuity and Remote Checkpoint Protocol

## Why this exists

Agent context can end before a phase is complete. Uncommitted files, local commits and stashes may not be available to another chat or machine. A pushed Git branch is the durable handoff.

Agents cannot reliably wait for a specific remaining-token threshold. Checkpoints are therefore based on milestones and elapsed work, not token estimates.

## Branch flow

```text
main
  └── work/phase-1-foundation
       ├── checkpoint: module structure
       ├── checkpoint: navigation shell
       ├── checkpoint: persistence wiring
       └── phase completion
```

Use `work/phase-N-description`. Never use `main` as the active development branch.

## Normal checkpoint

1. Finish one logical unit.
2. Update `docs/SESSION_STATE.md`.
3. Run:

```bash
bash scripts/checkpoint.sh "add navigation shell"
```

The remote branch then becomes the recovery point for a different chat.

## Emergency checkpoint

When the code is valuable but temporarily failing:

1. Record the exact failure, last command and next fix in `SESSION_STATE.md`.
2. Run only on a work branch:

```bash
CHECKPOINT_SKIP_TESTS=1 bash scripts/checkpoint.sh "wip: browser detector compile failure documented"
```

The resulting commit is intentionally marked `wip`. The next agent must repair it before phase completion.

## Resume procedure

A new chat must:

```bash
git fetch --all --prune
git status
git branch --show-current
git log -5 --oneline
```

Then read `AGENTS.md`, `PHASE_STATUS.md`, `HANDOFF.md` and `SESSION_STATE.md`, check out the recorded work branch, pull it and run the recorded validation before editing.

## Rules

- A local commit is not sufficient; confirm push success.
- A stash is not a handoff.
- Never use the emergency path to bypass normal testing at phase completion.
- Never checkpoint sensitive files.
- Keep WIP commits on work branches; they can be squashed when a phase is accepted.
