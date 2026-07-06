# Evolution Protocol — How This System Improves Itself

> Harness doc G. The harness is code for AI behavior; it gets maintained with
> the same discipline as code, and with a strong bias against growth.

## The two living files

- **`STATE.md`** — current truth: open items, blocked items, pending on-device
  checks, unexplained working-tree changes. Overwritten freely; keep under ~80
  lines by deleting resolved items (git history preserves them).
- **`LESSONS.md`** — append-only incident log: `date | what went wrong | root
  cause | rule changed (or "none yet")`. One line each.

Everything else in `docs/HARNESS/` is **rules**, changed only via the process below.

## When a rule changes

| Trigger | Action |
|---|---|
| A workflow step was wrong/missing and caused rework | Fix the workflow in the same session; log in LESSONS with the doc touched. |
| The same LESSONS root cause appears **2×** | Mandatory: encode a preventing rule (or a W4 refactor trigger). A repeated lesson with "rule changed: none" twice is itself a failure. |
| User corrects AI behavior ("don't do X", "always Y") | Encode it the same session — in SAFETY_RULES (permissions), OPERATING_SYSTEM (process), or AI_TASK_LIBRARY (contract). User corrections never live only in chat or AI memory. |
| Code reality drifts from harness docs (S5 signal) | Update the doc in the same commit as the code change that caused drift. DOMAIN_MODEL semantics changes are 🟡 — confirm with user. |
| A rule was never exercised in ~6 months or duplicates another | Delete it. Note the deletion in LESSONS. |

## Update rules for updates (meta-rules)

1. **One place per fact.** Before adding, search HARNESS docs for an existing
   home. Duplicated rules rot in one copy; cross-reference instead
   (`see SAFETY_RULES §…`).
2. **Rules must be checkable.** A new rule needs a trigger condition and a
   pass/fail test a small model can apply. "Be careful with parsers" is not a
   rule; "never commit a regex without a verbatim-sample test case" is.
3. **Prefer deletion to addition.** Each doc has a soft budget: if
   OPERATING_SYSTEM exceeds ~10 workflows or any doc stops fitting in one
   read, consolidate before extending.
4. **Don't encode one-offs.** A mistake made once by a capable model under
   unusual conditions gets a LESSONS line, not a rule. Rules are for repeatable
   failure modes.
5. **Harness changes are 🟢** (docs), but a change that *loosens* SAFETY_RULES
   zones is 🟡 — user confirms.
6. **CLAUDE.md vs HARNESS split**: CLAUDE.md holds upstream/global coding
   conventions and the pointer to this harness; fork-specific process lives
   here. Don't grow CLAUDE.md.

## Memory hygiene (for Claude-family sessions)

Private AI memory may cache pointers ("harness exists at docs/HARNESS") and
user preferences, but **facts about code/project state belong in STATE.md**,
where every model and the user can see and correct them. When memory and
STATE.md disagree, STATE.md wins; fix the memory.

## Quarterly review (or when the user asks "clean up the system")

1. Read LESSONS.md; every root cause with ≥2 hits must map to a rule — fix gaps.
2. Delete resolved STATE items, stale UNVERIFIED markers, unexercised rules.
3. Verify file paths named in HARNESS docs still exist (they drift when code
   moves); fix or remove.
4. Confirm the README routing table still routes every request type seen in
   recent sessions.
