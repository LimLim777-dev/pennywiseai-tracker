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
- **Fifth batch DONE 2026-07-06** (Fable 5, last): (14) doc-drift cleanup —
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
- **`SupportedBanksDocTest` red**: `currencyMeta` map lacks MYR. Fix per W1
  step 7 (`"MYR" to CountryMeta("Malaysia", "🇲🇾", "RM")`), regenerate
  `docs/supported-banks.json`.
- **Old Malaysian tests location**: `TestMalaysianParsers.kt` predates the
  per-parser standard; new tests go in per-parser classes. Migration is
  optional cleanup, W4-gated.

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
