# Engineering Review — 2026-07-06 (Fable 5 session)

> **Status update (same day):** C2, C3, H1, H2, H3, H6, M-A1, M-D1, M5 and
> the top frontend items are FIXED (see STATE.md batches 1–4; builds +
> full unit suite green). Still open: C1 (tombstone, 🔴 needs explicit
> user OK + migration plan), H4 (dead-letter), H5 (prefs into backup),
> M-R1/M-R2, dual-navigation merge, and the doc-deviation list D1–D8.
> C3 note: the atomic trio landed (insert + rule audit + subscription
> advance in one Room transaction, advance after confirmed insert);
> a lastPaidAt same-cycle guard remains available as optional hardening.

> Full-codebase review against the documented architecture (docs/ +
> docs/HARNESS/). Condensed to what future sessions need to act. Severity:
> C=Critical, H=High, M=Medium. Each finding has file:line anchors — verify
> they still hold before fixing (code moves).

## Critical / High correctness findings

| # | Finding | Where | Fix sketch | Effort |
|---|---|---|---|---|
| C1 | Soft delete rewrites `transaction_hash` to `DELETED_…`, so the processor's "skip previously deleted" guard (looks up the ORIGINAL hash) is dead code → **full rescan resurrects user-deleted transactions**. Also contradicts DOMAIN_MODEL §1 "nothing hard-deletes" (hard-delete methods exist). | `TransactionDao.softDeleteTransaction` (~:160) vs `SmsTransactionProcessor.saveParsedTransaction` (~:112) | Tombstone table for released hashes (or keep hash + separate `hash_released` flag). 🔴 zone (hash semantics) — needs explicit user OK + migration for existing `DELETED_` rows. | 1d |
| C2 | `RuleEngine` REGEX_MATCHES compiles user regex with no try-catch → one malformed rule throws inside evaluate → catch-all in processor drops **every future matching-type transaction silently**. | `RuleEngine.kt` ~:172 | `runCatching { Regex(...) }`; validate at rule-save; cache compiled per rule id. | 2h |
| C3 | No transaction boundary in ingestion; subscription `updateNextPaymentDateAfterCharge` runs BEFORE insert is confirmed and has no `lastPaidAt` re-entry guard (violates DOMAIN_MODEL §4) → duplicate/concurrent channels can double-advance the date. | `SmsTransactionProcessor` ~:163-198; `SubscriptionRepository` ~:118 | Wrap in `database.withTransaction`; advance after rowId != -1; add lastPaidAt same-cycle guard. | 1d |
| H1 | Amounts stored as TEXT via `toString()`; dedup window query uses TEXT equality `amount = :amount` → "250" ≠ "250.00" → cross-channel dedup + Boost TRANSFER reclassification silently fail. (Author knew: `findRecentExpensesByMerchantAndAmount` already uses CAST REAL.) | `Converters.fromBigDecimal`; `TransactionDao.getTransactionByAmountAndDate` ~:377 | Use `CAST(amount AS REAL) BETWEEN ±0.01` like the other query. | 1h |
| H2 | `deleteRule` on an ACTIVE GENERATE_DAILY_INCOME rule doesn't cancel the periodic worker or end the subscription → phantom income continues forever with no visible rule to turn off. | `RulesViewModel.deleteRule` ~:213 vs `toggleRule` OFF path ~:146 | Reuse toggle-OFF cleanup in deleteRule. | 30m |
| H3 | Autopay delete-rewind runs `generateIncomeAutopayUseCase.execute()` BEFORE the row is deleted → same-day hash still occupied → nothing regenerates AND nextPaymentDate advances. | `TransactionDetailViewModel.deleteTransaction` ~:854 | Delete first, then rewind + execute. | 15m |
| H4 | Parse/save exceptions only Log.e — no dead-letter record (SMS path has no equivalent of `bank_notifications` log) → losses undiagnosable. | `SmsTransactionProcessor` ~:199 | Write failures to unrecognized/dead-letter table; show in Notification Log. | 0.5d |
| H5 | SharedPreferences (`deleted_system_templates`, `hidden_accounts`) not in backup → restore on new phone resurrects deleted templates / unhides accounts. | `BackupModels.PreferencesSnapshot` | Add to PreferencesSnapshot (defaults! backup contract). | 0.5d |
| H6 | `transactions` table has ONE index (unique hash). Every list/date/bank query full-scans; Room Flows re-run all of them on every write. | `TransactionEntity` :12 | Add indices: `date_time`, `(is_deleted, date_time)`, `(bank_name, account_number)`. Additive migration (W6). | 2h |

## Medium (top ones)

- M-A1 `matchTransactionToSubscription` doesn't check the TRANSACTION's type
  → a refund (INCOME) with matching merchant/amount advances the EXPENSE sub.
  One-line filter. (`SmsTransactionProcessor` ~:163)
- M-D1 `getTotalAmountByTypeAndPeriod`: SUM over mixed currencies, returns
  Double, ignores excluded_from_analytics. Currently uncalled — DELETE it.
- M-R1 Screenshot confirm activities bypass the documented pipeline (no
  cross-channel dedup, no merchant mapping, no subscription match; rules get
  empty smsText). Extract a `saveManualTransaction` entry into
  SmsTransactionProcessor.
- M-R2 OCR regex logic welded to Context/Uri/ML Kit — not JVM-testable.
  Extract pure `parseText(String)`; freeze the 6 real TNG OCR samples as
  tests.
- M5 `bank_notifications` has no retention policy (grows forever, keeps
  sensitive bodies). Add 30-day cleanup on ingest.
- M6 Dual navigation (typed `PennyWiseNavHost` + string `MainScreen`) —
  every new screen must be registered in BOTH or it silently no-ops
  (bitten in practice on 2026-07-06 with Notification Log).
- UX: ConfirmActivity amount is not editable — OCR mis-reads force full
  manual re-entry.

## Documented-architecture deviations (fix docs in the same commit as code)

- D1 DOMAIN_MODEL "nothing hard-deletes" vs hard-delete DAO methods.
- D2 DOMAIN_MODEL "lastPaidAt guard mandatory" vs unguarded path (C3).
- D3 DOMAIN_MODEL §4 missing "Daily" billing cycle (code supports it).
- D4 TNG screenshot channel + TNGConfirmActivity + autopay-delete-rewind +
  GENERATE_DAILY_INCOME absent from DOMAIN_MODEL/pipeline diagram.
- D5 architecture.md claims encryption/feature-flags/crash-reporting that
  don't exist — delete the claims.
- D6 CLAUDE.md references `/prd.md` which doesn't exist.
- D7 Undocumented: ±5% subscription amount tolerance; balance `.max(ZERO)`
  clamp; screenshot path skipping mapping/dedup.

## Missing tests that would have caught the above

1. delete → rescan same SMS → not resurrected (fails today, C1)
2. equal-amount different-scale dedup window (fails today, H1)
3. concurrent dual-channel charge → subscription advances once (C3)
4. malformed regex rule → ingestion still works (fails today, C2)
5. `advance()` cycle table incl. month-end (Jan 31 + 1 month)
6. TNG OCR real-sample regression suite (after M-R2 extraction)

## Frontend improvement backlog (added same day, user asked "前端还有什么能进步")

Ordered by value/effort for THIS user's workflows:

1. ~~Confirm-dialog amount editable~~ **DONE 2026-07-06** (TNG + ShopeePay:
   amount is an editable field with validation; Add disabled until valid).
2. **Notification Log: per-entry "copy body" button + sender filter.** The
   log is the user's sample-collection tool for every parser task (already
   used to confirm the Maybank loan sample); long-press copy of the raw
   body directly feeds the W1/W2 workflow. Small: one IconButton +
   clipboard. Also add the 30-day retention while in there (review M5).
3. **TNG OCR debug dialog: wrap text in `SelectionContainer`** so the raw
   OCR text can be selected/copied instead of screenshotted back.
4. **Error surfacing**: replace `printStackTrace` + generic "Failed to
   delete transaction" with snackbar + reason; ViewModels already have
   errorMessage flows — they just receive constant strings.
5. **Dual-navigation merge** (M6) — the biggest structural frontend item;
   every new screen currently must be registered twice or silently no-ops.
   W4-gated; do it before the Investment tab lands (that tab must touch
   BOTH systems + NavigationRail anyway — merging first would halve that
   work and end the bug class).
6. **God-file splits**: ManageAccountsScreen 2285 lines /
   TransactionDetailScreen 2239 / SettingsScreen 1371 — extract section
   composables (pure moves, no logic). Do opportunistically when a task
   already touches the file, not as a standalone campaign.
7. **Transaction list paging** (Paging3 or windowed LIMIT) — defer until
   the user feels lag; indices (H6, landed) push that day out.
8. **Amount-entry ergonomics**: the manual add flow could default the
   date to "today 12:00" and focus amount first — micro-friction that
   matters because tap-to-pay UOB purchases must be entered manually
   (cashback tracker accuracy depends on the user actually doing it).

## Positives (do not "fix")

`ALL_MIGRATIONS` single source; backup compatibility contract + two-layer
guard tests; 113 parser-core test files; issue-number-anchored comments;
`BackupImporter` uses withTransaction (the pipeline should copy it);
SubscriptionDao direction=EXPENSE filter is correct and well-commented.
