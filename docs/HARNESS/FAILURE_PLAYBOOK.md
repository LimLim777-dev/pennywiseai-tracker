# Failure Playbook — Getting Unstuck Without Burning the User's Time

> Harness doc F. Applies to any AI session working on this repo, any model size.

## Detection signals (you are stuck when…)

- **S1 — Loop**: you are about to try essentially the same edit/command a 3rd time.
- **S2 — Blind guessing**: you are writing a regex/behavior without a real
  sample or reproduction (violates W1/W2 preconditions).
- **S3 — Expanding blast radius**: fixing X now "requires" touching files in a
  stricter safety zone than the task started in.
- **S4 — Environment fight**: build/test failures unrelated to your diff
  (SDK paths, Gradle daemon, JDK) persisting after one clean retry.
- **S5 — Contradiction**: code reality contradicts harness docs, memory, or
  the user's description.
- **S6 — Unverifiable**: the change can only be proven on the physical phone
  and no unit-level proxy exists.

## Retry limits (hard)

| Situation | Limit | Then |
|---|---|---|
| Same failing test/pattern (one hypothesis) | 2 attempts | switch hypothesis (R2) |
| Distinct hypotheses for one bug | 3 | escalate (R3) |
| Build/environment retries | 1 clean retry (`gradlew --stop`, then rebuild) | escalate with the exact error (R3) |
| Waiting on cloud build (Actions) | check result once; if failed, 1 diagnosis pass | escalate |

Count attempts honestly. "Attempt" = any edit-run cycle aimed at the same idea.

## Recovery steps

**R1 — Reduce.** Re-read the matching workflow's preconditions. Most stuck
states are a skipped precondition (no sample, no reproduction, wrong layer).
Produce the *smallest* failing example (a unit test) before another fix attempt.

**R2 — Switch hypothesis.** Write one line: "H1 (…) disproven because …; H2 is …".
Never abandon a hypothesis silently — the record is what keeps retries honest
(and feeds `LESSONS.md`).

**R3 — Escalate to the user.** Deliver, in plain language:
1. what was attempted (one line per hypothesis),
2. what is known/ruled out,
3. **exactly one** thing you need (a sample, a screenshot, a backup export, a
   decision) — phrased so a non-programmer can do it from the phone.
Then **stop working on that item** and switch to any other open `STATE.md`
item, or end the session. Do not fill waiting time with speculative changes.

**R4 — Abandon the approach** when: a fix would require a 🔴 zone change; or
the third hypothesis failed; or the fix requires information that does not
exist (e.g., a notification the bank never sends). Record in `STATE.md` as
`blocked` with the reason, and in `LESSONS.md` if a rule would have prevented
the dead end. Abandoning an *approach* is normal; silently abandoning the
user's *goal* is not — always state what the goal now waits on.

## Escalation policy — when to stop and ask

Ask the user **immediately** (don't attempt first) when:
- a 🟡/🔴 safety-zone action is on the path (SAFETY_RULES.md),
- real message samples are missing (W1/W2 precondition),
- the request is a product decision disguised as a task ("should transfers to
  Mum count as expense?" — taxonomy is human judgment, DOMAIN_MODEL final section),
- anything involves money movement, publishing, or upstream PRs.

Ask **after exhausting limits** for everything else.

## Known permanent limitations (never retry these as if they were bugs)

- HONOR battery manager killing the notification listener → user-side
  whitelist settings only. If a transaction is missing and the message exists
  on the phone, suspect this first and say so.
- UOB tap-to-pay & most ShopeePay spending emit no capturable signal → manual
  entry / future PDF import; do not try to conjure parsers for them.
- Notification-only banks provide no historical backfill (unlike SMS) — missed
  while dead = gone; `bank_notifications` log only covers what the listener saw.
