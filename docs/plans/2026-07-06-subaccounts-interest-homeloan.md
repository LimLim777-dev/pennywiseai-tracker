# Plan: Savings Sub-Accounts with Daily Interest + Home Loan Tracking

> Handover plan written 2026-07-06 (Fable 5 session). Execute task-by-task in
> order within each phase; any capable model can pick up any unstarted task.
> Every task states its inputs, files, safety zone, and acceptance criteria.
> Track progress by checking boxes here AND updating `docs/HARNESS/STATE.md`.

## Product decision (approved by user 2026-07-06 — record of scope)

The user wants to track, inside PennyWise:

1. **Boost Bank** — main account + **Jars** (daily interest)
2. **GX Bank** — main account + **Pockets** (daily interest)
3. **Ryt Bank** — main account + **Pocket** (daily interest) + **Invest**
4. **ShopeePay Money+** (daily interest)
5. **TNG eWallet GO+** (daily interest)
6. **Home loan at Public Bank** — outstanding balance RM515,412.22 as of
   2026-07-06; track remaining balance over time.

**This changes DOMAIN_MODEL §1 edge case 3.** That rule currently says
wallet-internal moves (Boost Jar, TNG GO+, Shopee Money+) are "not
transactions at all — parsers must return null". Under the new model each
sub-product becomes a **first-class manual account**, and moves between main
wallet and sub-account become `TRANSFER` transactions (own-account moves,
excluded from income/expense analytics as always). Daily interest becomes
real `INCOME` (category "Interest").

⚠️ T1.1 below updates DOMAIN_MODEL in the same commit as the first parser
change (EVOLUTION_PROTOCOL: doc drift fixed in the causing commit).

## Architecture (decided — do not re-design per task)

### Sub-account = existing manual-account model (#469/#470)

Each sub-product is one row-family in `account_balances`, keyed
`(bank_name, account_last4)`:

| Product | `bank_name` | `account_last4` | alias example |
|---|---|---|---|
| Boost Jar (one per jar) | `Boost Jar` | jar slug, e.g. `JAR1` | user's jar name |
| GX Pocket (one per pocket) | `GX Pocket` | pocket slug | pocket name |
| Ryt Pocket | `Ryt Pocket` | `PCKT` | — |
| Ryt Invest | `Ryt Invest` | `INVST` | — |
| ShopeePay Money+ | `ShopeePay Money+` | `MNYP` | — |
| TNG GO+ | `TNG GO+` | `GOPL` | — |

Use `AccountBalanceRepository.ensureManualOpening` / `recomputeManualBalance`
(the OPENING-anchor model). All rows `currency = "MYR"` explicitly.
**Ryt Invest is NOT in this plan** — investment accounts follow a different
value model (market moves are never income); see
`2026-07-06-investments-tracking.md`, which also requires excluding
INVESTMENT-type accounts from DeriveInterestUseCase routing.

### Interest derivation (core new piece, JVM-testable)

`DeriveInterestUseCase(bankName, accountLast4, observedBalance, observedDate)`:

1. `derived = opening + Σ(signed transactions + transfer legs)` — reuse
   `AccountBalanceRepository.signedTransactionSum` + transfer-leg logic.
2. `delta = observedBalance − derived`
3. `delta > 0` → insert one `INCOME` transaction:
   - category `"Interest"`, merchant = the account's display name,
   - `dateTime = observedDate.atStartOfDay()`, currency `MYR`,
   - description `"Interest (derived from balance update)"`,
   - **idempotent hash** `interest-<bankName>-<last4>-<observedDate>`
     (same-day re-observation replaces nothing; second observation same day
     is dropped by the hash — document this in the UI copy).
   - Then `recomputeManualBalance` so balance == observation.
4. `delta < 0` → do **not** fabricate anything. Surface to the user
   ("balance lower than expected by RM X — add a fee/withdrawal?").
5. `delta == 0` → no-op.

Granularity truth (record in DOMAIN_MODEL): interest is only as
daily-accurate as the observations. Daily notification parser (if the bank
sends one) or daily balance observation → daily rows; weekly observation →
one row per week. This is inherent, not a bug.

### Capture channels per wallet (automation ladder, most→least automatic)

0. **Accessibility-service balance reader** (zero-action; EXPERIMENT — see
   T2.6): an `AccessibilityService` scoped to the wallet packages reads the
   balance TEXT from the UI node tree whenever the user opens the wallet app
   normally. No screenshot, no OCR, no extra user action. ⚠️ Malaysian
   banking apps commonly detect accessibility services (BNM screen-scraping
   guidance) and may refuse to run — must be trialed PER APP on the real
   device before building readers; keep the service package-scoped and easy
   to toggle off.
1. **Notification parser**: if the wallet sends a daily-interest
   notification, parse it as INCOME "Interest" directly (skip derivation).
   Needs a verbatim sample first — W1 hard precondition. (User confirmed:
   Boost sends none; others unverified.)
2. **Screenshot OCR balance reader**: reuse the `ScreenshotObserver` →
   parser → ConfirmActivity pattern (see `TNGScreenshotParser`). The
   screenshot is auto-detected; taking it is the only manual step. NOTE
   review finding M-R2: put the OCR-text→result logic in a **pure
   `parseText(String)` function** so real OCR samples become JUnit cases.
3. **Manual balance update** (always works, ships first): the existing
   "Update balance" flow on a manual account, wired to call
   `DeriveInterestUseCase` instead of silently overwriting.

### Daily granularity without daily observations: accrual + true-up (T1.5)

The user wants day-level interest rows but rates float and observations are
irregular. Standard accrual accounting solves it:

- A daily worker posts an **estimated** interest row per tracked account:
  `balance × rate / 365`, description marks it `(estimated)`, hash
  `interest-est-<bank>-<last4>-<date>` (idempotent). Rates live in a small
  per-account config the user edits (seed from the Collected-data table).
- Whenever a **real** balance observation arrives (channel 0/2/3),
  `DeriveInterestUseCase` computes the TRUE accumulated interest since the
  last observation, deletes the estimated rows in that window, and posts one
  correction-backed set (or adjusts the final day) so the books equal
  reality. Floating rates and caps are absorbed at every true-up.
- Estimated rows are real INCOME in analytics (they're a good-faith
  accrual), but visibly marked and always reconciled by the next true-up.

Combined maximum automation: **channel 0 feeding true-ups + T1.5 daily
accrual** = daily rows with zero routine effort. Screenshot OCR remains the
fallback wherever accessibility is blocked.

### Home loan = existing LoanEntity (no schema change for v1)

`LoanEntity(personName = "Public Bank Home Loan", direction = BORROWED,
originalAmount = <user's original loan if known, else 515412.22>,
remainingAmount = 515412.22, currency = "MYR",
note = "Baseline outstanding as of 2026-07-06")`.

Monthly installments link via the existing `loanId` + `loanContribution`
mechanism ("Mark as loan" sheet). `loanContribution` = the **principal
portion** only — the interest portion stays plain EXPENSE. v1: user enters
the split manually per payment (Public Bank statements show it).
v2 (optional, later): amortization estimate from rate — needs the user's
current effective rate; Malaysian home loans are daily-rest variable, so
any auto-split is an estimate. Do not build v2 until v1 is used for a month.

---

## Phase 0 — Hygiene before new features (1 session)

Rationale: the new interest transactions depend on exactly the pipeline
paths where the 2026-07-06 engineering review found correctness bugs
(`docs/reviews/2026-07-06-engineering-review.md`).

- [ ] **T0.1 Commit the working tree** (W7). The tree holds ~33 pre-existing
      modified files + this session's TNG/rules/review work, and `main` is
      ahead of origin. Ask the user before touching the 33 pre-existing files
      (SAFETY_RULES: pre-existing dirty tree); commit this session's files in
      logical commits and push. Zone: 🟡 (git discipline).
- [ ] **T0.2 Review quick fixes #1–#6** from the review doc (regex
      try-catch, deleteRule cleanup, autopay delete ordering, subscription
      match type filter, dedup CAST REAL, delete dead SUM query). Each has
      file:line in the review doc. ~1 day total, mostly 🟡 zone — get user OK
      once for the batch. Acceptance: each fix has its regression test.

## Phase 1 — Foundation (shared by all five wallets; 1–2 sessions)

- [x] **T1.1 DOMAIN_MODEL update** (DONE 2026-07-08, same commit as T1.2) (🟡): rewrote §1
      edge case 3 (wallet-internal moves become TRANSFERs between own
      accounts once the sub-account exists), add §7 "Sub-accounts & interest"
      describing the table above, the derivation rule, and the granularity
      truth. Add "Daily" to §4 billing cycles (code already supports it —
      doc drift found in review D3).
- [x] **T1.2 `DeriveInterestUseCase`** DONE 2026-07-08 + 8 unit tests (pure JVM: fake
      repository, cases: positive delta, negative delta, zero, same-day
      idempotency, transfer-in same day not counted as interest). Files:
      `app/.../domain/usecase/DeriveInterestUseCase.kt`, test alongside
      existing app tests. Zone: 🟢 new file, but touches money semantics →
      treat 🟡, show the user the test list.
- [ ] **T1.3 Wire "Update balance" to derivation**: in the manual-account
      update flow (ManageAccountsScreen → its ViewModel), when the account
      is one of the sub-account bank_names above, route through
      `DeriveInterestUseCase` with a confirm step ("RM2.15 higher than
      expected → record as interest?"). Other manual accounts keep current
      behavior. Zone: 🟡 (accounts area, pre-existing dirty files — ask user
      per T0.1 first).
- [ ] **T1.4 Seed the accounts**: guided by the user in-app (create manual
      accounts with the naming from the table + current balances from the
      Collected-data section). No code needed if ManageAccounts already
      supports manual account creation with custom bank name — verify
      first; gap-fill if the bank name field is a fixed dropdown. Output:
      user has all sub-accounts visible with correct balances.
- [ ] **T1.5 Daily accrual + true-up** (see architecture section): rate
      config per tracked account (seed from Collected data), daily worker
      posting estimated rows, true-up logic inside `DeriveInterestUseCase`
      (delete estimates in window, post reality). JVM tests: estimate
      idempotency, true-up replaces estimates exactly, rate change mid-window
      absorbed. Ships after T1.2; independent of Phase 2.

## Phase 2 — Per-wallet capture upgrades (one TP-1/TP-2 task each; sample-gated)

For each wallet below, the FIRST step is asking the user for samples
(AI_TASK_LIBRARY sample-collection protocol). If a daily-interest
notification exists → parser path. If not → screenshot OCR reader (channel
2) or stay on manual (channel 3). **Never write a regex without a verbatim
sample** (W1).

- [ ] **T2.1 Boost Jars** (`BoostParser.kt` currently nulls jar moves):
      **jar-withdraw sample already collected** (see Collected data below) —
      the withdraw direction can be built now. Still need the top-up
      direction sample. No daily-interest notification exists
      (user-confirmed) → interest via channel 2/3. Change jar moves from
      `null` to TRANSFER (between `Boost Bank` main and the named
      `Boost Jar` account). Tests per sample.
- [ ] **T2.2 GX Bank Pockets** (`GXBankParser.kt`): need Pocket in/out
      samples. **Pockets A–D are fixed-term** (see Collected data): create
      them as accounts with the maturity note; on 2026-08-31 record one
      INCOME "Interest" of RM253.88 each (idempotent hash
      `interest-GX Pocket-<slug>-2026-08-31`) — a dated reminder in
      STATE.md is enough, no scheduler needed.
- [ ] **T2.3 Ryt Bank Pocket** (`RytBankParser.kt` if exists — verify):
      same shape.
- [ ] **T2.4 ShopeePay Money+**: no notifications expected → screenshot OCR
      reader for the Money+ page (new `MoneyPlusScreenshotParser` following
      `TNGScreenshotParser`, pure `parseText()` + samples as tests), feeding
      DeriveInterestUseCase via a confirm dialog.
- [ ] **T2.5 TNG GO+**: `TNGEWalletParser.isTransactionMessage()` currently
      filters GO+ lines (fork changed it to filter only pure-GO+ messages).
      Need samples of GO+ daily-earnings message (if any) and GO+ cash-in/out.
      Cash-in/out → TRANSFER to/from `TNG GO+`. Daily earnings → INCOME
      Interest. Screenshot fallback: GO+ page reader like T2.4.
- [ ] **T2.6 Accessibility balance reader (EXPERIMENT — channel 0)**:
      step 1 is a compatibility probe, NOT a full build: minimal
      `AccessibilityService` scoped to the five wallet packages that only
      logs whether each app still runs normally with the service enabled
      (several MY banking apps block accessibility). User trials each app on
      the device and reports. Only where compatible, step 2 builds the
      node-tree balance extractor (pure `parseNodes()` → testable) feeding
      `DeriveInterestUseCase` as automatic true-ups. Zone: 🟡 (new
      permission surface; user must enable the service in system settings —
      provide exact steps). If an app blocks it: that wallet stays on
      screenshot/manual, note it in STATE.md as permanent.

Each T2.x acceptance: `:parser-core:jvmTest` green including new cases;
factory/notification-config/docs synced (W1 steps); on-device checklist
delivered ("top up RM1 into Jar → expect one TRANSFER, no EXPENSE/INCOME").

## Phase 3 — Home loan (independent; can run parallel to Phase 2)

- [ ] **T3.1 Create the loan record** (no code expected): verify the Loans
      screen can create a BORROWED loan with MYR currency and the amounts
      above. If the UI forces INR or lacks BORROWED direction in create flow,
      gap-fill (🟡). Output: loan visible with RM515,412.22 remaining.
- [ ] **T3.2 Verify link-payment flow reduces remaining**: check
      `LoansViewModel` — does linking a transaction (loanId +
      loanContribution) decrement `remainingAmount`, or is remaining manual?
      Whichever it is, document it in the Loans section of DOMAIN_MODEL and
      make sure a linked installment with loanContribution = principal
      portion reduces outstanding by exactly that amount. Add a unit test.
- [ ] **T3.3 Public Bank installment capture**: blocked on the SAME sample
      as the existing STATE.md open item "`TO_REGEX` in PublicBankParser.kt
      is UNVERIFIED". Ask the user for the verbatim monthly installment
      deduction notification. Then W2: parse it (EXPENSE, merchant
      "Public Bank Home Loan"), and consider a Rule to auto-set category
      "Housing". The user then marks it as loan with the principal split.
- [ ] **T3.4 (v2, optional — do not start unasked)**: amortization
      estimator. Needs: current effective rate, remaining tenure. Produces
      suggested principal/interest split per payment. Product decision
      required on how to show estimates vs statement truth.

## Collected data (from user, 2026-07-06) — seed values & samples

> Rates below are for context only. **Never compute interest from rates** —
> every one of them floats or has caps/bonuses. Interest = balance-delta
> derivation, always.

### Verbatim notification samples (usable for parser tests NOW)

Boost jar withdraw (T2.1):
```
Title: Jar Withdraw
Text:  You've withdrawn RM 10.00 from Unlimited Jar to ****7499. View your jar details.
```
→ TRANSFER: from `Boost Jar`/Unlimited → Boost Bank main (****7499).
Boost has **no daily-interest notification** (user-confirmed) → Jars use
channel 2/3 (screenshot/manual observation).

Maybank loan payment (T3.3), collected 2026-07-06, separator
**user-confirmed comma**:
```
Maybank2u
You've paid RM 2,300.00 to the PUBLIC BANK loan account ending ****0010. REF: 544237901M
```
Parse → EXPENSE RM2,300, merchant "Public Bank Home Loan" (loan account
****0010), via `MaybankMAEParser` (check whether "You've paid … loan
account" is already matched; likely a new pattern → W2 with this sample).

### Seed balances (as of 2026-07-06)

| Account | Balance | Rate (context) | Note |
|---|---|---|---|
| Boost Jars 1–8 (eight jars) | RM65.18 each | 3.0% p.a. | ~RM0.01/day each |
| Boost Celebration Jar | RM65.18 | 3.0% p.a. | |
| Boost EdgeProp Jar | RM60.18 | 3.3% p.a. | |
| Boost Unlimited Jar | RM5,130.69 | 3.3% p.a. | biggest jar, ~RM0.46/day |
| GX main account | RM95.24 | 2.0% p.a. | |
| GX Pockets 1–10 (ten) | RM95.49 each | 2.0% p.a. | |
| GX Pockets A–D (four) | RM12,500.00 each | fixed-term | **matures 2026-08-31 at RM12,753.88 each** (+RM253.88) — model as fixed term, ONE maturity INCOME each, no daily tracking |
| Ryt main | RM132.07 | 2.05% p.a. | |
| Ryt Pocket | RM20,031.13 | 4.0% p.a. **first RM20k only** | excess above 20k earns base rate |
| ShopeePay Money+ | RM10,177.75 | first RM1k +5% bonus, rest ~3.64% **floating** | |
| TNG GO+ | RM323.99 | ~3.07% p.a. **floating** | |

Ryt **Invest** balance not provided — ask only if/when the user funds it.

### Home loan facts

Monthly installment **RM2,300**, due **1st of month**, paid by **manual
transfer from Maybank** (no auto-deduction). Consequence for T3.3: there is
no Public Bank deduction notification to parse — the capture signal is the
Maybank MAE outgoing-transfer notification (`MaybankMAEParser` already
exists in this fork). Need one verbatim sample of that monthly transfer
notification to pin a test + a Rule (match ~RM2,300 transfer on ~1st →
category "Housing", rename merchant "Public Bank Home Loan"). Add an
EXPENSE-direction subscription ("Public Bank Home Loan", RM2,300, Monthly,
1st) for reconcile + reminder; user marks the captured transfer as loan
payment with the principal split. Automation option (v1.5): when a captured
transaction matches the subscription, offer one-tap "link to home loan"
instead of the manual Mark-as-loan flow.

### Effort-proportional tracking recommendation (product guidance)

Daily observation only pays off for the big balances: Ryt Pocket (~RM2.19/d),
Money+ (~RM1.06/d), Unlimited Jar (~RM0.46/d), GO+ (~RM0.03/d). The
twenty-odd RM65–95 jars/pockets earn ~RM0.01/day each — weekly or monthly
balance updates are fine there; the derivation records the accumulated
interest correctly regardless of observation frequency.

## Still missing (ask when the task needs it)

1. **Boost**: Jar **top-up** (deposit) notification verbatim (have withdraw ✓).
2. **GX**: Pocket in/out notification verbatim; whether GX daily interest
   posts a notification or is in-app only.
3. **Ryt**: Pocket in/out notification verbatim.
4. **Money+ / GO+ page screenshots** (OCR samples for T2.4/T2.5 readers).
5. **房贷**: 手动转账是从哪家银行转出的？（决定用哪个 parser 捕获）；
   可选——原始贷款总额和当前利率（只有做 v2 本金/利息自动拆分才需要）。

## Sequencing recommendation

`T0.1 → T0.2 → T1.1+T1.2 → T1.3+T1.4 → (T2.x 按样本到达顺序) ∥ (T3.1 → T3.2 → T3.3)`

Phase 1 alone already delivers value: all six sub-accounts visible with
manual balance updates producing interest records. Phases 2–3 automate.
