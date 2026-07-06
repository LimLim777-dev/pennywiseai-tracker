# AI Task Pattern Library

> Harness doc D. Reusable execution patterns. Each pattern is self-contained:
> a small model given the pattern + its input should not need any further
> prompting. Patterns reference workflows (W1–W8) in `OPERATING_SYSTEM.md`
> for the step detail; this file defines the *contract* (input, output,
> constraints, verification).
>
> The user is a non-programmer. Output contracts therefore end with plain
> instructions ("install this, tap that"), not with code talk.

---

## TP-1 Add bank/wallet support (→ W1)

**Input format** (request from user; ask for exactly this if missing):
```
Bank/wallet name: <name>
Channel: notification / SMS
If SMS: sender number(s) as shown on the phone
If notification: nothing else needed (AI finds the package name; if unknown, ask user for a screenshot of the notification)
Samples (verbatim, one per line, personal numbers may be masked with X):
  OUT:  <full message text>
  IN:   <full message text>
  (other variants: FPX, QR pay, cashback, etc.)
```
**Steps**: W1 steps 1–8.
**Constraints**: `getCurrency()="MYR"`; never guess regexes; wallet-internal
moves return null; new test class per parser (not `TestMalaysianParsers.kt`).
**Output format**: list of files created/changed; table `sample → parsed
(amount, type, merchant)`; any UNVERIFIED gaps; whether an APK rebuild is
needed for the user to see the effect.
**Verification**: `.\gradlew :parser-core:jvmTest` green (incl. regenerated
bank catalogue, W1 step 7); every sample covered by a test case.

## TP-2 Fix a wrongly captured transaction (→ W2 or W3)

**Input format**:
```
What the app shows: amount / merchant / type / bank as displayed
What it should be: <correction>
The original message (verbatim) if available; otherwise a screenshot of the transaction detail screen
```
**Steps**: reproduce in a parser test (W2). If the parser output is already
correct, the defect is app-layer → W3 (check `RuleEngine` applications and the
listener's dedup/reclassification windows before touching code).
**Constraints**: minimal diff; existing tests stay green; consider whether a
user-editable Rule (`DOMAIN_MODEL.md` §6) solves it without code.
**Output format**: root-cause sentence (which layer), fix description, new test
case, on-device expectation.
**Verification**: failing test → green; regression suite green.

## TP-3 Fix a UI bug (→ W3)

**Input format**:
```
Screen: (e.g. Home / Transactions / a transaction's detail / Subscriptions)
Steps to see it: <taps>
What happens vs what should happen (screenshot welcome)
```
**Steps**: W3. Locate screen in `app/.../presentation/<area>/` or
`app/.../ui/screens/`; state lives in the matching ViewModel.
**Constraints**: don't change ViewModel/repository logic for a purely visual
fix; respect Material3 + `PennyWiseScaffold` conventions; both light/dark themes.
**Output format**: file(s) changed, one-line cause, on-device check ("open X,
you should now see Y").
**Verification**: compiles; on-device check named for the user.

## TP-4 Data model / schema update (→ W6, risky)

**Input format**: the field/feature needed and which entity it belongs to.
**Steps**: W6 (read `docs/backup-format.md` first — non-negotiable).
**Constraints**: additive-only without explicit approval; Kotlin defaults on
every serialized field; DB version bump + schema JSON export.
**Output format**: entity diff, migration approach (auto/manual), backup
compatibility statement ("old backups restore because …"), test evidence.
**Verification**: `BackupSchemaGuardTest` + app unit tests green; schema JSON
for the new version exists in `app/schemas/`.

## TP-5 Refactor a service/module (→ W4, gated)

**Input format**: target files + the trigger that justifies it (user request /
blocking structure / repeated LESSONS entry). No trigger → decline per W4.
**Constraints**: behavior-freezing tests first; declared scope only.
**Output format**: scope declaration, before/after structure sketch, proof
tests unchanged-and-green.
**Verification**: full test suite for the touched module green; diff contains
no logic changes.

## TP-6 Debugging an unknown failure (→ W3 + FAILURE_PLAYBOOK)

**Input format**: whatever the user has — symptom description, screenshot,
`.pennywisebackup` export. Ask for the backup export when data state matters
(Settings → export); it contains all transactions/subscriptions as JSON.
**Steps**: form ≤3 ranked hypotheses from the pipeline diagram; test the
cheapest first (unit test or reading code, before asking the user to do
anything on-device); after each disproven hypothesis, say so and move on.
**Constraints**: retry limits from FAILURE_PLAYBOOK (2 attempts per
hypothesis, 3 hypotheses); no state-changing "fixes" while still diagnosing.
**Output format**: cause (or ranked remaining suspects + exactly what evidence
would discriminate), then the fix via TP-2/TP-3.
**Verification**: the original symptom's reproduction path, re-run.

## TP-7 Build & deliver APK (→ W8)

**Input format**: "build me the app" / any completed change the user wants on the phone.
**Steps**: W8.
**Output format**: link/location of the Actions run, which APK to pick
(**arm64-v8a**), install note, and the on-device checklist.
**Verification**: Actions run green; `STATE.md` updated with pending on-device checks.

## TP-8 Sync with upstream (requires human judgment — assisted, not automatic)

**Input format**: user asks to pull upstream changes, or wants an upstream feature.
**Steps**: `git fetch upstream` (add remote `sarim2000/pennywiseai-tracker` if
missing); summarize divergence (`git log --oneline upstream/main ^main` and the
reverse); **present conflicts touching fork-modified files
(`BankNotificationConfig`, listener service, Malaysian parsers, factory) to the
user before merging anything.**
**Constraints**: never force-push; never auto-resolve conflicts in fork-owned files.
**Output format**: divergence summary, conflict list, recommendation, then act
only on the user's choice.
**Verification**: post-merge `:parser-core:jvmTest` + app compile green.

## TP-9 "Remember/track this" (state, not code)

**Input format**: user reports a fact ("salary lands on the 1st via Maybank,
format attached", "new UOB short code 12345").
**Steps**: if it changes code (new sender → `KNOWN_SENDERS` in
`UOBCardParser`; new format → TP-1/TP-2), do that. Otherwise record it in
`STATE.md` under the matching open item.
**Output format**: what was recorded/changed and which open item it closes.

---

## Sample-collection protocol (used by TP-1/TP-2)

When asking the user for samples, always:
1. Ask for the **full message text**, copied verbatim (long-press → copy);
   screenshots acceptable.
2. Explicitly say account digits may be replaced by `X` but **wording and
   punctuation must not be edited**.
3. Ask "in" and "out" separately — banks word them differently.
4. For notifications that disappear: the app logs raw captured notifications
   (`bank_notifications` table via `BankNotificationRepository`); a backup
   export may already contain the sample.
