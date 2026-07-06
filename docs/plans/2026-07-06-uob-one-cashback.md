# Plan: UOB One Card — Cashback & Minimum-Spend Tracker

> Handover plan written 2026-07-06 (Fable 5 session). Companion to
> `2026-07-06-subaccounts-interest-homeloan.md`; independent — can be built
> in parallel. Blocked on ONE user input (the tier table below).

## User requirements (stated 2026-07-06)

- Card: **UOB One** (Malaysia). Track the cashback program: current cycle
  spend, **how much more minimum spend is needed** for the next tier, and
  the estimated cashback.
- Statement cycle **closes on the 17th** each month (cycle = 18th of month
  M-1 → 17th of month M).
- **Transaction date ≠ post date**: purchases post a few days late; UOB
  counts by POST date, so spend near the cycle close may fall into the next
  cycle. The tracker must model this.

## Hard constraint to surface in the UI (do not hide it)

Per FAILURE_PLAYBOOK known limitations, **UOB tap-to-pay purchases emit no
SMS/notification** — the app cannot see them. The tracked cycle spend is
therefore a **lower bound**. Mitigations, in order: (a) manual entry of
tap-to-pay purchases; (b) monthly statement **PDF import** (existing blocked
STATE item — this feature raises its priority; framework reference
`GPayPdfParser.kt`); (c) show the "captured spend" label, never "total
spend". A tracker that silently undercounts toward a cashback threshold is
worse than none — the user might stop spending thinking they've hit the
tier when UOB's own count says otherwise. The safe direction is the
opposite: show captured spend as the floor and let the user reconcile
against the UOB app mid-cycle.

## Design

### Data (mostly exists)

- UOB card transactions already flow in via SMS (senders 67425/66300,
  `UOBCardParser`, type CREDIT). No new capture work.
- `account_balances.statement_day` column **already exists**
  (MIGRATION_38_39) — set it to 17 for the UOB card account.
- New small config: cashback tier table (see "Needed from user"). Store as
  a fork-local config object first (constants file), UI editor later only
  if rules change often.
- Optional new column (only if per-transaction override proves necessary):
  `posting_cycle_override` on transactions — decide during T-C3, additive
  (W6 rules apply: Kotlin default + backup contract).

### Cycle assignment with post-date lag

- `estimatedPostDate = transactionDate + postingLagDays` (config, default 2,
  user-tunable once they observe their real lag).
- A transaction belongs to the cycle whose window contains its
  estimatedPostDate.
- Transactions whose transactionDate is within `postingLagDays + 1` days of
  the 17th are **uncertain**: show them in a separate "may post next cycle"
  bucket and exclude them from the "confirmed" progress number (they appear
  as a lighter segment on the progress bar). Allow one-tap reassignment to
  either cycle when the user sees the real post date in the UOB app.

### UI (one screen/section, e.g. from the UOB account card)

- Current cycle header: window dates + days until the 17th close.
- Progress bar: confirmed captured spend → next tier threshold, with the
  uncertain bucket as a lighter segment; "RM X more to reach tier Y".
- Estimated cashback at the current tier (per the tier table), clearly
  marked "estimate, captured transactions only".
- Tier table displayed with the active tier highlighted.

## Rebate rules (T-C1 COLLECTED 2026-07-06 — from official UOB T&C PDF)

### Rebate tables (per statement month, computed on POSTING date)

**User's card: ONE Classic (confirmed 2026-07-06).** Active config:
threshold **RM800** min retail spend per statement month; Petrol /
Groceries / Dining / Grab earn **10%** above threshold (0.2% below),
**capped RM10 per category**; Other Retail Spend always 0.2%, uncapped.

(Platinum variant for reference, in case of a future upgrade: RM1,500
threshold, RM15 caps. Keep both in the tier config; select Classic.)

Practical consequence for the UI: at 10%, the per-category cap lands at
**RM100 spend/category** — show per-category "sweet spot" progress (e.g.
"Dining RM80/RM100 — RM20 more still earns 10%"), plus the overall
RM800 min-spend progress bar. Max monthly rebate from the 10% categories
= 4 × RM10 = RM40.

### Category definitions (MCC-based; SMS carries NO MCC → see mapping note)

- **Petrol**: MCC 5541/5542; includes Setel CardTerus at pump; **Setel
  Wallet top-up NOT eligible**.
- **Groceries**: MCC 5411 at named chains: AEON Big, AEON Supermarket,
  Ben's Independent Grocer, Cold Storage, Econsave, Everise, Giant, Jaya
  Grocer, Lotus's (Tesco), Mercato, Mydin, Servay, Village Grocer,
  Maxvalue, The Food Merchant.
- **Dining**: MCC 5812/5814.
- **Grab**: spend via Grab app — GrabRides 4121, GrabExpress 4215, GrabFood
  5814, GrabMart 5499, **GrabPay 6540 (top-up EARNS — the one top-up
  exception)**, GrabSubscription 4789.
- Everything else eligible → Other Retail Spend (0.2%).

**Mapping note**: UOB SMS carries merchant text, not MCC. Rebate-category
attribution must map merchant names → rebate category (the groceries chain
list above is directly encodable; petrol brands + "GRAB*" prefixes
likewise). Attribution is therefore approximate — show category assignment
on each transaction and let the user reassign (feeds a MerchantMapping-style
override). GrabFood-via-Grab vs regular 5814 Dining is indistinguishable
from merchant text alone; default "GRAB*" → Grab category.

### Excluded from BOTH rebate and min-spend computation

Balance transfers · cash withdrawals · IPP/EPP/Flexi plans · government
transactions incl. **utility bills** & postal · insurance (Liberty, credit
shield premium) · annual fees/interest/late fees/service charges · JomPAY ·
**PIB (Personal Internet Banking) transactions** · top-ups (except GrabPay)
· alimony/bail/charity · refunded/disputed transactions (refunds also
retroactively REDUCE min spend — a refund can un-qualify a month).

### Cycle definition — evidence from real statements (2026-07-06)

Two real statements reviewed (MAY 2026: statement date 17 MAY; JUN 2026:
statement date 17 JUN):

- Cash-rebate credit lines post **ON the statement date (17th)** and are
  included in that same statement → the 17th itself belongs to the closing
  statement. Default window: **[18th M-1, 17th M]** (user's reading).
- Statements print **Transaction Date only** (no posting-date column). JUN
  statement's retail lines span txn dates 17 MAY → 15 JUN; MAY statement
  spans 16 APR → 16 MAY. Latest included txn date ≈ statement date − 2 →
  **default postingLagDays = 2 is empirically supported**.
- Keep `cycleStartDay` configurable anyway (cheap insurance).
- Other card facts: ONE Classic Visa, last4 **2695**, credit limit
  RM21,000, payment due = statement date + 20 days (e.g. 17 JUN → 07 JUL),
  minimum payment ≈ 5% (RM65.39 on RM1,307.83).

### Engine acceptance fixtures (REAL rebate outcomes — tests must reproduce)

JUN 2026 statement (retail purchase RM1,339.85 ≥ RM800 → 10% tier active):

| Category | Rebate credited | Implied eligible spend |
|---|---|---|
| Dining | RM10.00 **(capped)** | ≥ RM100 |
| Petrol | RM8.00 | RM80.00 |
| Grocery | RM6.28 | RM62.80 |
| Grab | RM3.92 | RM39.20 |
| Others | RM1.82 | RM910 @ 0.2% |

MAY 2026 statement (retail purchase RM1,008.49 ≥ RM800):
Petrol RM10.00 (capped) · Grocery RM10.00 (capped) · Dining RM10.00
(capped) · Grab RM7.63 · Others RM1.08. Three categories capped — exactly
the "sweet spot" the tracker should surface.

T-C2 test task: reconstruct each statement's transaction list from the user
(or T-C5 PDF import), run the engine, assert the five rebate numbers per
statement. Any mismatch = category-mapping or exclusion bug.

### Merchant → rebate-category mapping evidence (from real statements)

- Petrol: `SETEL` (multiple RM30–50 lines).
- Groceries: `GIANT HYPER`, `AEON CO`, `LOTUS'S`, `MR DIY`? (no — MR DIY is
  Others), `SUNWAY HOLDINGS-FARLIM`? unclear — verify against rebate math.
- Dining: `ZUS COFFEE`, `PYX*DAILY COFFEE`, `ROCK TILL DAWN KITCHEN`,
  `SUSHI KING`, `LIME FIRES`, `65.ONDO`, `FOURDUAN SDN. BHD.`(?).
- Grab: `GRAB-EC`, `GRAB RIDES-EC`.
- Others (observed): `MTRUSTEE BERHAD` (recurring RM1–3), `LAZADA`,
  `TIMEDOTCOM`, `MAXIS-AUTO`, `BOOKMYSHOW`, `COWAY (R)`, `ECOSHOP`,
  `ENRICH`, `PARA THAI`(?), `TIMEDOTCOM`.
- ❓ `GREATEASTERN…` RM300 insurance premium appears as a retail line —
  whether it counted toward min spend / Others rebate is NOT derivable from
  the summary; the T&C exclusion list names "Liberty insurance" + "credit
  shield premium" specifically, not all insurers. Resolve during fixture
  reconciliation (line-by-line rebate math will reveal it).

### Statement PDF structure (pre-work for T-C5 import)

Columns: `Transaction Date | Transaction Description (merchant + city + MY)
| RM | Transaction Amount`, credits suffixed `CR`; summary boxes: Previous
Balance / Credit-Payment / Debit-Fees / Retail Purchase / Cash Advance /
Total Balance Due; rebate lines appear as `CASH REBATE <CATEGORY> … CR` on
statement date. Reference for the future `UOBStatementPdfParser`
(framework: `GPayPdfParser.kt`). Still need an actual PDF file (not
screenshots) to build it.

## Tasks

- [x] **T-C1 Tier table** — collected; card = **Classic**; cycle evidence +
      TWO real-statement rebate fixtures collected (see above). Nothing
      blocks T-C2 anymore.
- [x] **T-C2 Cycle + rebate engine** — **DONE 2026-07-06** (Fable 5):
      `domain/service/UobCashbackEngine.kt` (pure JVM: UobTierConfig
      CLASSIC/PLATINUM, cycle windows w/ short-month clamp, estimated-post
      assignment, boundary-uncertainty via posting-range spanning, capped
      rebates floored to the cent, sweet-spot remaining) +
      `UobCategoryMapper` (T&C grocery list, petrol brands, GRAB prefix,
      overrides hook for user reassignment). 12 tests green in
      `UobCashbackEngineTest`. NOTE for T-C3: engine takes ELIGIBLE
      transactions — the caller must apply the T&C exclusion list and
      category attribution (mapper + user overrides) before calling.
      Real-statement reconciliation (May/Jun fixtures above) remains a
      calibration step for the MAPPER, not the engine math.
- [ ] **T-C3 Screen + wiring** (W3/TP-3 shape): read UOB card transactions
      (CREDIT type, bankName UOB), feed the engine, render. Register the
      screen in BOTH navigation systems (review finding M6 — typed
      PennyWiseNavHost AND string-route MainScreen, or it silently no-ops).
- [ ] **T-C4 Reconciliation affordance**: one-tap reassign for uncertain
      transactions; manual-entry shortcut for tap-to-pay purchases
      (pre-filled bank/account/type).
- [ ] **T-C5 (later, big win): UOB statement PDF import** — unblocks exact
      post dates AND back-fills invisible tap-to-pay spend. Waiting on a
      redacted statement PDF (existing STATE item).

## Needed from user (给用户)

1. ~~cashback 规则表~~ ✅；~~卡种~~ ✅ ONE Classic；~~账期证据~~ ✅
   （rebate 记在 17 号当天且含在当期 → 默认 [18号, 17号]）；
   ~~post-date 延迟~~ ✅（实证 ≈2 天）。
2. （T-C5，可选）真实月结单的 **PDF 文件本体**（不是截图）——写
   `UOBStatementPdfParser` 用，同时解锁 tap-to-pay 盲区回填。结构已从
   截图记录（见上），只差文件。
