# Operating System — Deterministic Workflows

> Harness doc C. Every development action on this fork follows one of the
> workflows below. If a request doesn't fit any workflow, that is itself a
> signal: classify it first (see `README.md` routing table) or escalate.
>
> Conventions used here:
> - **Build**: `.\gradlew :app:assembleStandardDebug` (Windows, repo root)
> - **Parser tests**: `.\gradlew :parser-core:jvmTest` (parser-core is Kotlin Multiplatform — the task is `jvmTest`, not `test`)
> - **App unit tests**: `.\gradlew :app:testStandardDebugUnitTest`
> - Cloud build: push to fork → `.github/workflows/build-my-apk.yml` → install **arm64-v8a** APK.

---

## W1 — Add support for a new transaction source (new bank / wallet / message format)

**Trigger**: user provides a message the app doesn't capture, or names a new
bank/wallet to support.

**Hard precondition**: at least one **verbatim** real message sample per
direction (in/out). If missing → stop, request samples using the format in
`AI_TASK_LIBRARY.md` TP-1. Never write a regex from an imagined sample; label
any unavoidable guess `// UNVERIFIED: no real sample yet` and record it in `STATE.md`.

**Steps** (notification channel; for SMS skip step 3 and match `canHandle()`
on the SMS sender ID instead):
1. Create `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/<Name>Parser.kt`
   extending `BankParser`. Override `getCurrency() = "MYR"` (mandatory for
   Malaysian sources), `getBankName()`, `canHandle()`, and the `extract*`
   methods needed.
2. Register it in `BankParserFactory` (same package).
3. Add the app package → alias mapping in
   `app/.../receiver/BankNotificationConfig.kt`. The alias string must equal
   what `canHandle()` accepts — mismatches fail silently.
4. Apply self-transfer handling: if the counterparty name is present in the
   message, route through `SelfTransferDetector`. If the incoming direction
   has no name (Boost-style), note it — the cross-bank window in
   `BankNotificationListenerService` is the fallback.
5. Skip wallet-internal moves (Jar/GO+/Money+ pattern): return `null`.
6. Write tests in
   `parser-core/src/test/kotlin/com/pennywiseai/parser/core/bank/<Name>ParserTest.kt`
   following `docs/parser-test-standards.md` (JUnit5 `@TestFactory` +
   `ParserTestUtils`). One case per real sample, plus one negative case
   (OTP/promo message returns null). *(New tests go here even though older
   Malaysian tests sit in `TestMalaysianParsers.kt` — do not add to that file.)*
7. Sync the bank catalogue — **never hand-edit `docs/supported-banks.json`**;
   it is generated from the factory registry by `SupportedBanksDocTest`:
   a. If the parser's currency is new to the `currencyMeta` map in
      `parser-core/.../SupportedBanksDocTest.kt`, add it
      (MYR → `CountryMeta("Malaysia", "🇲🇾", "RM")`).
   b. Regenerate: `scripts/update-supported-banks.sh` (bash), or on Windows
      `$env:UPDATE_SUPPORTED_BANKS='true'; .\gradlew :parser-core:jvmTest --tests "com.pennywiseai.parser.core.SupportedBanksDocTest" --rerun-tasks`.
   Also note channel/quirks in `STATE.md`.
8. Run parser tests. Then W7 (commit) and, if the user wants it on the phone, W8 (release).

**Acceptance criteria**:
- [ ] All provided samples parse to the expected amount/type/merchant/currency `MYR`
- [ ] Negative case returns null
- [ ] `:parser-core:jvmTest` green, including `SupportedBanksDocTest` after
      the step-7 regeneration
- [ ] Factory + notification config + docs all updated in the same commit

**Failure handling**: regex doesn't match sample → fix the regex, never "clean up"
the sample. Two failed attempts on the same pattern → FAILURE_PLAYBOOK R2.

---

## W2 — Edit an existing parser (format change / new message variant / misparse)

**Trigger**: a transaction is captured wrong (amount, merchant, type,
duplicate) or a new wording variant appears.

**Steps**:
1. Reproduce first: add a failing test case with the verbatim sample to the
   parser's test class. If you cannot make it fail, the bug is downstream
   (processor/listener/rules) → switch to W3.
2. Fix the parser with the **narrowest** change; keep all existing test cases green.
3. Check the fix against `DOMAIN_MODEL.md` §1 edge cases (self-transfer,
   wallet-internal, dedup) — the classic regression is a broadened regex that
   starts matching internal-move messages.
4. Run `:parser-core:jvmTest`. Commit (W7).

**Acceptance**: new case green, zero previously-green cases broken.

**Failure handling**: if the fix requires loosening a pattern so far it could
match other banks' text, stop and record the conflict in `STATE.md` — overlap
between parsers is a human-judgment call.

---

## W3 — Fix a bug outside parser-core (app layer)

**Trigger**: wrong behavior in capture pipeline, UI, analytics, budgets,
subscriptions.

**Steps**:
1. Classify the layer using the pipeline diagram in `DOMAIN_MODEL.md` §0:
   listener/receiver → processor → repository → viewmodel → UI.
2. Read the involved file(s) fully before editing. Check `SAFETY_RULES.md`
   zone for each file you intend to touch; risky/critical zones need the
   user's OK first.
3. Write or adapt a unit test when the logic is JVM-testable (repositories,
   engine, viewmodels). UI-only fixes: state the manual verification step you
   expect the user to perform (which screen, which action, expected result).
4. Fix with minimal diff; do not refactor in the same change (see W4).
5. Run the relevant test task + compile the app. Commit (W7).

**Acceptance**: reproduction path closed; test or explicit on-device
verification instruction delivered; no unrelated files changed.

**Failure handling**: can't reproduce or can't locate layer after two focused
passes → FAILURE_PLAYBOOK R1 (report findings, ask for a screenshot/export
instead of guessing).

---

## W4 — Refactoring

**Trigger**: **only** one of: (a) the user explicitly asks; (b) a W1–W3 task is
*blocked* by structure (state why in one sentence); (c) the same mistake
recurred ≥2 times in `LESSONS.md` and a structural change would prevent it.
Never refactor opportunistically — this fork optimizes for stability under
small-model maintenance, not elegance.

**Steps**:
1. Declare scope: files in, behavior frozen. List the tests that pin current behavior.
2. If pinning tests don't exist for the touched logic, write them *before* refactoring.
3. Refactor in reviewable steps; each compiles and passes tests.
4. Confirm zones (SAFETY_RULES.md): refactors inside critical zones require
   explicit user approval regardless of trigger.

**Acceptance**: identical observable behavior (tests prove it), diff limited to
declared scope.

**Failure handling**: scope creep detected (touching files not declared) → stop,
re-plan. Broken pin test → revert, don't patch the test.

---

## W5 — Validation (what "done" means, by change type)

| Change type | Required proof |
|---|---|
| parser-core change | `:parser-core:jvmTest` green incl. new cases |
| entity/DAO/migration | app compiles; schema JSON exported (`app/schemas/.../<v>.json`); migration test or manual plan; `BackupSchemaGuardTest` green |
| backup-serialized field | Kotlin default present; `BackupSchemaGuardTest` green |
| pipeline (listener/processor/worker) | unit tests where possible **+ named on-device check for the user** |
| UI | compiles + named on-device check (screen, action, expected) |
| docs/harness | internal consistency read-through |

A change is **not done** until its row's proof exists. On-device checks are
delivered as an explicit checklist in the final message, e.g.
"Send RM1 from GXBank to Boost; expect one TRANSFER row, no INCOME row."

---

## W6 — Database schema change

**Trigger**: any edit to `app/.../data/database/entity/*` or `PennyWiseDatabase.kt`.

**Steps**:
1. Read `docs/backup-format.md` and `docs/database-migrations.md` first (hard rule from CLAUDE.md).
2. New columns: Kotlin default value **and** `defaultValue` in `@ColumnInfo`;
   `@Contextual` for BigDecimal/dates; `@Serializable` for enums.
3. Bump DB version in `PennyWiseDatabase.kt`; prefer auto-migration; verify the
   new schema JSON appears under `app/schemas/`.
4. Run `BackupSchemaGuardTest` + app unit tests.
5. This is a **risky zone** — present the migration plan to the user before
   committing if any existing column is renamed/dropped/retyped (destructive);
   purely additive changes may proceed.

---

## W7 — Commit discipline

**Trigger**: any workflow above completes its acceptance criteria.

**Steps**:
1. One logical change per commit; include code + tests + doc sync together.
2. Message: imperative summary line mentioning the bank/screen affected
   (matches existing history style, e.g. "UOB parser: strip processor prefix").
3. Never commit: secrets, PII beyond the already-accepted
   `SelfTransferDetector` owner constant, `local.properties`.
4. Do not push unless the user asked, or the task is a release (W8).
5. If the working tree contains unrelated pre-existing modifications (it
   currently does), commit **only your files** (`git add <paths>`, never `git add -A`)
   and leave the rest untouched; note them in `STATE.md` if unexplained.

---

## W8 — Release to phone

**Trigger**: user wants to test/use a change on the device.

**Steps**:
1. All W5 proofs green locally.
2. Commit (W7), push to `origin main` (this fork's working branch — user
   approval to push is implied by a release request, not by anything else).
3. GitHub Actions `build-my-apk.yml` runs `:app:assembleStandardDebug`.
4. Tell the user: download the **arm64-v8a** APK from the Actions artifacts,
   install, and run the on-device checklist from W5.
5. Record the release + pending on-device checks in `STATE.md`.

**Failure handling**: Actions build fails but local build passed → diff the
environment (JDK/SDK versions in the workflow file) before touching code.

---

## Session protocol (every AI session, any model)

**Start**: read `docs/HARNESS/README.md` → `STATE.md` → the one workflow that
matches the task. Do not re-scan the whole repo.
**End**: update `STATE.md` (what changed, what's pending, what's blocked on the
user); append to `LESSONS.md` if anything went wrong (EVOLUTION_PROTOCOL.md).
