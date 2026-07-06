# Problem Diagnosis — Why This Project Depends Too Much on Prompting

> Harness doc A. Written 2026-07-03 from a full repository scan.
> Scope: the PennyWise Malaysia fork (`LimLim777-dev/pennywiseai-tracker`),
> maintained by one non-programmer user + AI sessions.

## 1. Project reality (constraints the system must accept)

- **One user, no human developers.** All code is written by AI sessions; the
  user supplies real bank message samples, tests APKs on a physical phone
  (HONOR LGN-NX1), and states goals.
- **The fork will not merge back upstream.** Upstream (`sarim2000/pennywiseai-tracker`)
  keeps moving; local `main` is used as the working branch.
- **Verification is split**: parser logic is verifiable locally
  (`gradlew :parser-core:jvmTest`); end-to-end behavior (notification capture,
  background service survival) is verifiable **only on the user's phone**.
- **Real message samples are the raw material.** No parser can be correctly
  written or fixed without a verbatim sample. This is a hard input dependency,
  not a process defect.

## 2. Why AI prompting is currently required too often

| # | Cause | Evidence |
|---|-------|----------|
| 1 | **Project knowledge is scattered across four places**: `CLAUDE.md` (upstream conventions), `../项目交接文档.md` (Chinese handover doc, outside the repo), Claude's private memory files (invisible to other tools/models), and chat history (lost). A new session must be re-briefed by prompt. | Handover doc lives at `D:\Claude Code - Personal\PennyWise\项目交接文档.md`, not in the repo; its "known issues" list is already stale (Boost fix landed in commit `4eeea33d`). |
| 2 | **Multi-step checklists exist only in AI memory.** Adding a bank touches up to 5 places (parser, `BankParserFactory`, `BankNotificationConfig`, tests, regenerating the bank catalogue via `scripts/update-supported-banks.sh`) but no repo file states the full checklist for the *notification* path — `CLAUDE.md` covers only the SMS path. | `SupportedBanksDocTest` fails permanently because step 5 was never encoded anywhere. |
| 3 | **No task classification.** Every request arrives as free-form prose; the AI re-derives "what kind of task is this and what does done mean" each time. | Every past session began with re-explaining the workflow. |
| 4 | **No recorded definition of done.** "Works" currently means "user flashes APK and watches notifications", which is slow and prompts follow-up sessions for the same bug. | Public Bank outgoing regex is guessed and unverified; salary format still unknown; both tracked only in the handover doc. |
| 5 | **Session-to-session state is untracked.** 33 modified files + 2 unpushed commits + 1 untracked worker sit in the working tree with no note of intent. The next session must reverse-engineer what half-finished work is. | `git status` on 2026-07-03. |

## 3. Where workflows are not deterministic

- **Parser addition**: registration order in `BankParserFactory`, package-name
  alias matching `canHandle()`, and doc sync are all implicit conventions.
  Forgetting any one produces silent non-capture or a red doc test.
- **Test placement**: standards require per-parser JUnit5 test classes under
  `parser-core/src/test/kotlin/com/pennywiseai/parser/core/bank/` using
  `ParserTestUtils` (`docs/parser-test-standards.md`), but the Malaysian tests
  live in a single file at the source-root (`parser-core/src/test/kotlin/TestMalaysianParsers.kt`).
  Two conventions now coexist; a small model will copy whichever it sees first.
- **Currency**: entity defaults are `"INR"` (`TransactionEntity.currency`,
  `BudgetEntity.currency`, `SubscriptionEntity.currency`). Every Malaysian code
  path must explicitly say `MYR`; nothing enforces this at the parser layer
  except each parser's `getCurrency()` override. One forgotten override = data
  silently recorded in the wrong currency.
- **Commit/release**: no rule for when to commit, what to include, or when to
  trigger the `build-my-apk.yml` Actions build. Work accumulates uncommitted.
- **Verification**: no rule for which changes need on-device testing vs which
  are proven by unit tests alone.

## 4. Where logic is scattered or implicit

- **Self-transfer detection lives in two layers**: name-based detection in
  `parser-core/.../bank/SelfTransferDetector.kt` (parser layer) and
  amount/time-window cross-bank reclassification in
  `app/.../receiver/BankNotificationListenerService.kt:109-125` (app layer,
  Boost-specific hardcode). Nothing documents that both exist or when each fires.
- **Dedup logic exists twice**: `transaction_hash` unique index (DB layer) and
  the ±2-minute same-amount window in the notification listener (app layer).
- **The app already has a deterministic rule system** (`domain/service/RuleEngine.kt`,
  `RuleEntity`, priority-ordered conditions/actions) that could absorb
  categorization/renaming requests users currently ask the AI to hardcode —
  but no doc says "prefer a Rule over code" for such requests.
- **Owner identity** (`MAH GUO REN`) is a load-bearing constant inside
  `SelfTransferDetector.kt` while `CLAUDE.md` says "never use PII in comments,
  code anywhere". The (already-made) judgment call that this constant is an
  allowed exception is recorded nowhere.

## 5. Where human intervention is unnecessarily frequent

| Currently asked of the user | Should be |
|---|---|
| Re-explaining project context at session start | Read `docs/HARNESS/README.md` (this system) |
| Deciding where a new parser/test file goes | Fixed by rule (OPERATING_SYSTEM.md W1) |
| Being asked "should I also update the docs/tests?" | Always yes; part of the workflow's acceptance criteria |
| Re-reporting the same known issues | Tracked in `docs/HARNESS/STATE.md` (single live state file) |
| Confirming trivially safe changes (new test case, doc sync) | Auto-proceed per SAFETY_RULES.md safe zone |

What **legitimately** stays with the user (do not automate): providing verbatim
message samples, on-device verification, product decisions (what to track, what
counts as noise), and approving anything in the critical zone.

## 6. Token / cognitive inefficiency points

- Every session re-scans the repo to rediscover structure that never changes
  → fixed by the routing table in `docs/HARNESS/README.md`.
- The 55-parser `BankParserFactory` and 2000-line entity set get re-read whole
  when only the Malaysian slice matters → DOMAIN_MODEL.md names the exact
  files that matter for this fork.
- Stale memory (e.g., "Boost bug unfixed") causes wasted verification loops →
  live state belongs in-repo (`STATE.md`), not in AI memory.
- Free-form bug reports trigger open-ended exploration → AI_TASK_LIBRARY.md
  defines input formats that make small models effective.

## 7. Explicitly *not* problems (do not "fix" these)

- Upstream architecture (MVVM + Clean, multi-module) is sound; no refactor needed.
- The permanent `SupportedBanksDocTest` failure is a symptom of #2 above, not
  a test to delete.
- HONOR background-kill of the notification listener is an OS-level issue with
  no code fix; it is a **requires human judgment / device settings** item, permanently.
