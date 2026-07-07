# STATE — Live Project State (overwrite freely; keep short)

> Read at session start, update at session end. When this disagrees with AI
> memory or old handover docs, **this file wins**.
> Last updated: 2026-07-06 (Fable 5 handover session).

## ⛳ Active plans (start here for feature work)

**`docs/plans/2026-07-06-payslip-import.md`** — payslip PDF import:
NETT PAY → INCOME Salary (idempotent per period), payroll EPF (employee +
employer) → INVESTMENT rows into KWSP dated 15th of following month
(KWSP reflects 12th–16th). Blocked on the actual PDF files as fixtures.

**`docs/plans/2026-07-06-investments-tracking.md`** — investments (RHB
Trade Smart / MooMoo USD+MYR (two rows) / KWSP / PMO Plus / myASNB;
Ryt Invest dropped) as INVESTMENT-type manual accounts: snapshot valuation
(market moves are NEVER income), contributions as INVESTMENT-type
transactions via Rules, dividends as INCOME when credited. **UI: Investment
is a new top-level bottom-nav tab** (Home · Analytics · Investment ·
Settings; both nav systems + NavigationRail). T-I1/T-I3 are unblocked;
transfer-notification samples arrive "when the user next tops up".

**`docs/plans/2026-07-06-subaccounts-interest-homeloan.md`** — user-approved
scope: Boost Jars / GX Pockets / Ryt Pocket+Invest / Money+ / GO+ as
first-class sub-accounts with daily interest, plus Public Bank home loan
(outstanding RM515,412.22 @ 2026-07-06). Phased tasks T0–T3 with acceptance
criteria; most Phase-2 tasks are **blocked on user samples** (checklist in
the plan doc, in Chinese, phone-doable). Recommended order: T0.1 commit tree
→ T0.2 review quick fixes → T1.x foundation → T2.x/T3.x.

**`docs/reviews/2026-07-06-engineering-review.md`** — full-codebase review.
C1–C3 are correctness bugs in the ingestion pipeline (deleted-transaction
resurrection on rescan; malformed-regex rule kills ingestion; subscription
double-advance). Fix T0.2 batch (#H2/H3/M-A1/H1/C2/M-D1, ~1 day) before
building new pipeline-dependent features.

## Open items (unblocked)

- **Fourteenth batch DONE 2026-07-07** (三份真实 UOB 账单 APR/MAY/JUN 驱动;
  plan: `docs/plans/2026-07-07-recurring-autodebits.md`):
  (23) **UOB engine calibrated** — rounding DOWN→HALF_UP + APR
  below-threshold statement pinned as a five-line acceptance fixture;
  settled: GREATEASTERN insurance COUNTS toward min spend/Others rebate
  (no exclusion needed); May "Others 1.08" in the plan doc was a typo
  (1.09). (24) **TimeParser landed** — Time Internet receipt (sender
  62003) → EXPENSE, bankName deliberately "UOB" (bill auto-charges the
  card, amount varies; keeps cashback min-spend honest); real masked
  fixture test + alias pinned. (25) **Expense-direction autopay** —
  schema **v58** (additive `subscriptions.auto_generate`, AutoMigration
  57→58, 58.json exported): EXPENSE subscriptions with the new
  "Auto-record on schedule" switch + optional "Charged to bank" field are
  phantom-created by the existing autopay creator (idempotent hashes);
  delete-rewind gated to INCOME only (deleting an auto-debit phantom must
  NOT resurrect it). User setup table + open questions (Maxis 83.90 vs
  statement 53.75; where Grab RM23.90 charges) in the plan doc. All app +
  parser-core tests green (only the documented pre-existing
  MalaysianParsersTest 7 remain red).

- ~~T0.1 commit discipline~~ **DONE 2026-07-07** (user-authorized "你处理吧"):
  entire tree committed in 6 logical commits (harness+docs / TNG channel /
  MAE daily-income / pipeline fixes+v56 / UOB engine / pre-harness WIP
  aggregate) and pushed — `main` now in sync with origin at `a023ac4a`.
  Actions `build-my-apk.yml` triggered by the push; user installs the
  **arm64-v8a** APK from that run's artifacts (carries all 15 fixes).
- ~~T0.2 review quick fixes~~ **DONE 2026-07-06** (Fable 5): all six landed
  + RuleEngineTest green + APK builds. (1) RuleEngine malformed-regex
  runCatching + 4 new tests incl. EXCLUDE_FROM_ANALYTICS regression;
  (2) RulesViewModel.deleteRule now ends subscription + cancels worker for
  GENERATE_DAILY_INCOME rules; (3) TransactionDetailViewModel autopay
  rewind moved AFTER delete (same-day regeneration works now);
  (4) SmsTransactionProcessor: only EXPENSE/CREDIT match subscriptions
  (refunds no longer advance dates); (5) TransactionDao
  getTransactionByAmountAndDate uses CAST REAL ±0.01 (scale-proof dedup
  window); (6) deleted getTotalAmountByTypeAndPeriod dead code (DAO +
  repository). Pending on-device: none of these need device checks except
  (3): delete today's Tabung Bonus → it should regenerate immediately.
  Remaining review items (C1 tombstone, C3 withTransaction, H4, H5)
  still open — see review doc.
- **Second batch DONE 2026-07-06** (Fable 5, same day): (7) **H6 indices**
  landed — transactions gains (is_deleted,date_time) / (date_time) /
  (bank_name,account_number); SCHEMA_VERSION 55→56, AutoMigration(55,56),
  56.json exported, backup tests green. (8) **Confirm-dialog amount now
  editable** in TNGConfirmActivity + ShopeePayConfirmActivity (validated
  decimal field, Add disabled until valid) — OCR misreads no longer force
  cancel-and-retype. (9) Frontend improvement backlog (8 ranked items)
  appended to the review doc — top next: Notification Log copy-body button
  + retention; merge dual navigation BEFORE building the Investment tab.
  On-device check for (8): pick a TNG screenshot → edit the amount in the
  dialog → saved transaction shows the edited amount.
- **Eleventh batch DONE 2026-07-07**: (21) **M-R1 landed** — screenshot
  confirm activities (TNG + ShopeePay) now save through
  `SmsTransactionProcessor.saveManualTransaction`: cross-channel ±2-min
  dedup (a late notification for the same receipt no longer double-records
  — user sees a Toast with the reason), merchant mapping, rules,
  subscription matching, all atomic. Manual path deliberately skips the
  tombstone check (re-importing a deleted receipt is a deliberate act).
  (22) **M-R2 landed** — `TNGScreenshotParser.parseText` +
  `ShopeePayScreenshotParser.parseText` are pure JVM functions;
  `TNGScreenshotParserTest` pins 5 regression cases from the real
  2026-07-06 OCR dumps (names masked). On-device check: screenshot a TNG
  receipt already captured by notification → Toast "Already recorded…",
  no duplicate row.
- **Tenth batch DONE 2026-07-07** (user approved "做"): (20) **C1 tombstone
  landed** — new `deleted_transaction_hashes` table (schema v57,
  MIGRATION_56_57 backfills tombstones from existing `DELETED_*` rows);
  user-initiated deletes (detail screen + notification action) write the
  ORIGINAL hash atomically with the soft delete; ingestion skips
  tombstoned hashes → **full SMS rescan no longer resurrects deleted
  transactions**. Rescan-rebuild hard deletes intentionally don't
  tombstone. Tombstones ride in backups (all privacy modes; defaults per
  contract; regression test added). On-device check: delete a captured
  transaction → Settings rescan → it stays deleted; manually re-adding
  the same amount/time still works. **Every review Critical/High is now
  closed.**
- **Ninth batch DONE 2026-07-07**: (19) **H5 landed** — backup now carries
  `account_ui` (hidden accounts + deleted system rule templates) with
  defaulted fields per the compatibility contract; importer only overwrites
  when the section carries values (old backups can't wipe device state);
  regression test decodes old-shaped JSON. **All review Criticals/Highs
  are now closed except C1** (🔴 tombstone — the only item awaiting the
  user's explicit go-ahead).
- **Eighth batch DONE 2026-07-07**: (18) **H4 dead-letter landed** — an SMS
  that parses but fails to SAVE (exception or unexpected non-dup failure)
  is now recorded into `unrecognized_sms` (unique sender+body dedups
  repeats) and surfaces on the Unrecognized SMS screen; duplicates/blocked/
  previously-deleted results are NOT dead-lettered. Review remaining: C1
  (🔴), H5 (prefs into backup), M-R1/M-R2, dual-nav merge, docs D-items
  already fixed.
- **Seventh batch DONE 2026-07-07**: (17) Settings → **PDF Text Debug** —
  pick any PDF, view/copy the runtime PDFBox extraction (SelectionContainer,
  monospace). This is the fixture-collection tool for T-P1 payslip parser
  AND the future UOB statement import (T-C5). **User action to unblock
  T-P1**: run the three payslip PDFs through it and paste the outputs to
  the next session (identity fields will be masked before committing).
- **Sixth batch DONE 2026-07-07** (Fable 5, refreshed window): (16)
  **UOB T-C3 screen landed** — Settings → "UOB One Cashback":
  `ui/screens/uob/UobCashbackScreen.kt` + ViewModel feed captured UOB
  transactions (bankName "UOB", CREDIT/EXPENSE) through UobCategoryMapper
  into the engine for the CURRENT cycle; shows RM800 progress, days left,
  per-category rebate with capped/sweet-spot hints, uncertain-boundary
  bucket, estimated total, captured-only disclaimer. Registered in BOTH
  navigation systems (typed UobCashback + string "uob_cashback").
  On-device check: Settings → UOB One Cashback → current-cycle numbers
  should roughly match the UOB app (captured spend is a lower bound).
  Remaining UOB tasks: T-C4 reassign affordance; mapper calibration
  against the May/Jun statement fixtures; T-C5 PDF import.
- **Fifth batch DONE 2026-07-06** (Fable 5): (14) doc-drift cleanup —
  DOMAIN_MODEL now documents the soft-delete hash-rename + C1 caveat, hard-
  delete paths, TNG/ShopeePay screenshot channel semantics (empty smsText,
  M-R1 gaps), Daily cycle, ±5% match tolerance, EXPENSE/CREDIT-only
  subscription matching, C3 ordering protection, GENERATE_DAILY_INCOME
  lifecycle; architecture.md false claims fixed (no encryption, aspirational
  sections marked); CLAUDE.md dead /prd.md reference removed. (15)
  `updateCategoryForMerchant` now skips deleted rows; RulesViewModel
  printStackTrace → Log.e. Two LESSONS.md entries added (pipeline-tests
  rule; never bulk-edit source via PowerShell text round-trips — it
  corrupted UTF-8 chars, caught and repaired same session). Build green.
- **Fourth batch DONE 2026-07-06** (Fable 5): (12) **C3 landed** —
  SmsTransactionProcessor: insert + rule-audit + subscription advance now
  commit atomically in `database.withTransaction`; the subscription date
  ADVANCE moved after insert confirmation (duplicates/losing concurrent
  channel can no longer advance dates); balance update isolated outside the
  transaction with its own catch (balance bug can't lose the transaction
  row). lastPaidAt-based same-cycle guard NOT added (semantics broader than
  the bug) — noted as optional hardening in the review doc. (13) TNG OCR
  debug dialog text is now selectable/copyable. Full app unit suite + APK
  green together. Review remaining: C1 (🔴 tombstone — needs explicit user
  OK), H4 (dead-letter), H5 (prefs into backup), M-R1 (screenshot path into
  pipeline), plus Medium/doc items.
- **Third batch DONE 2026-07-06** (Fable 5): (10) Notification Log
  gained a per-entry copy-body button (sample-collection workflow) +
  30-day retention for PROCESSED rows (unprocessed kept for diagnosis) —
  `BankNotificationDao.deleteProcessedOlderThan`, called opportunistically
  on each log insert. (11) **UOB T-C2 engine DONE** —
  `domain/service/UobCashbackEngine.kt` + `UobCategoryMapper`, 12 JVM
  tests green; next UOB step is T-C3 (screen + wiring; apply exclusions +
  mapper before calling the engine). Final APK of the day builds clean
  with everything included.
- ~~SupportedBanksDocTest red~~ **FIXED 2026-07-07**: MYR added to
  currencyMeta; `docs/supported-banks.json` regenerated — Malaysia section
  now lists all 8 fork parsers. Test green.
- **`MalaysianParsersTest` 7/13 red — PRE-EXISTING** (verified: identical
  failures at pre-session commit e0abbea5 via throwaway worktree; the
  2026-07-06/07 changes caused none of them). The old root-level
  `TestMalaysianParsers.kt` uses FABRICATED message samples (violates the
  W1 precondition) that drifted from the fork's parsers, while REAL
  on-device behavior for TNG/MAE/Boost is user-verified. Resolution:
  migrate to per-parser classes with REAL samples (W4-gated; samples on
  file: TNG OCR set, jar withdraw, Maybank loan payment). Do NOT bend
  parsers to satisfy fabricated fixtures.
  **The other 3 of the original 10 were a REAL bug, FIXED 2026-07-07**:
  `TBankParser.canHandle` substring-matched "TBANK", which "BOOSTBANK" and
  "RYTBANK" contain — and T-Bank registers before them in the factory, so
  single-parser resolution returned T-Bank for Boost/Ryt aliases. Capture
  survived (processor uses content-aware getParsers) but the notification
  listener's PRE-PASS used getParser → parse=null → the ±2-min
  cross-channel dedup AND the Boost INCOME→TRANSFER cross-bank
  reclassification (DOMAIN_MODEL §1 edge case 2) were **silently dead for
  Boost/Ryt**. Fixed: T-Bank canHandle now word-boundary; listener
  pre-pass now content-aware like the processor. On-device recheck: send
  RM1 GXBank→Boost; expect one TRANSFER, no INCOME row.

## Blocked on user input

- **Phase-2 remaining samples** (seed balances + Boost jar-withdraw sample
  + loan facts + **Maybank loan-payment sample** COLLECTED 2026-07-06 — see
  plan doc "Collected data"; capture goes through the existing
  MaybankMAEParser): still missing: Boost jar TOP-UP sample; GX Pocket
  in/out samples; Ryt Pocket in/out samples; Money+ / GO+ page screenshots
  (OCR samples). ⚠️ loan sample's amount separator (`2.300.00` vs
  `2,300.00`) unconfirmed — regex must tolerate both until verified.
- **UOB One cashback tracker** — FULLY unblocked: rules + card variant
  (**ONE Classic**: RM800 threshold, RM10/category caps) + cycle evidence
  (rebates post on the 17th, included → window [18th, 17th]; posting lag
  ≈2 days empirical) + **two real-statement rebate fixtures** for engine
  acceptance tests, all in `docs/plans/2026-07-06-uob-one-cashback.md`.
  T-C2 engine is the next buildable step. UOB statement PDF **structure**
  documented (pre-work for the long-blocked PDF-import item; still needs
  an actual PDF file).
- **Loan-payment sample confirmed** (comma separator, RM 2,300.00) —
  MaybankMAEParser W2 task in the subaccounts plan is fully unblocked.
- **T2.6 accessibility probe**: before building channel-0 readers, trial
  whether Boost / GX / Ryt / ShopeePay / TNG still run with a
  PennyWise AccessibilityService enabled (MY banking apps may block it).
  Needs the probe APK + user trying each app once.
- **Dated reminder**: 2026-08-31 GX Pockets A–D mature → record one INCOME
  "Interest" RM253.88 each (plan doc T2.2).
- ~~Salary~~ **FULLY UNBLOCKED 2026-07-06** — payslip PDF import is the
  sole salary record (`docs/plans/2026-07-06-payslip-import.md`): 3 real
  PDFs on user's machine at `D:\Downloads\ME 2026{01,04,06} PENE M33.pdf`
  (do NOT commit; extract + mask → text fixtures, procedure in plan);
  credit = last working day (weekend/holiday → day earlier), NO Maybank
  push notification exists. Plan also gained T-P4: financial-month view
  setting (salary funds next month — view-layer aggregation only, real
  dates never shift; shares the window abstraction with UOB T-C2).
- **UOB statement PDF import**: waiting on a redacted real statement PDF
  (framework reference: `GPayPdfParser.kt`).
- **UOB Credit Card category/cashback tracker**: user interest confirmed;
  waiting on card type + cashback tier rules before design.

## Pending on-device checks

- 2026-07-06 build (TNG screenshot flow): six TNG screenshot types parse
  correctly ✅ (user-verified); Smart Rules run on screenshot-confirmed
  transactions ✅ (user-verified). Remaining: ShopeePay screenshot still
  correct after the rule-engine wiring (regression check).

## Known state (fixed this period, for context)

- TNG screenshot OCR channel added: `TNGScreenshotParser` +
  `TNGConfirmActivity` + ScreenshotObserver fallback; rules now run on both
  confirm activities; `RuleEngine` EXCLUDE_FROM_ANALYTICS action fixed
  (previously swallowed when rule field was MERCHANT etc.).
- Settings → "TNG OCR Debug" shows raw ML Kit text for any picked image
  (regex diagnosis without adb).
- Deleted system rule templates persist via SharedPreferences
  `deleted_system_templates` (NOT in backup — review finding H5).

## Wishlist (user goals, not started)

Calendar/important-dates · quick-note prompt after auto-capture · tax-relief
marking (EPF/insurance) · budget suggestions from history (~RM1200–1500/mo
baseline) · cloud backup.
