# Plan: Recurring Auto-Debits with No SMS (UOB card silent charges)

> Written 2026-07-07 from THREE real UOB One statements (APR/MAY/JUN 2026,
> local files only — statements carry address/card PII and are NEVER
> committed). Statement evidence supersedes earlier user recollections
> where they conflict (flagged below).

## Problem

Several monthly bills auto-charge the UOB One card with **no SMS and no
notification** — invisible to the app, and missing from the UOB cashback
tracker's minimum-spend count:

| Merchant (statement text) | Amount | Statement-observed dates | Signal available |
|---|---|---|---|
| `GREATEASTERN…` (insurance) | RM300.00 | 31 MAR · 30 APR · 31 MAY | none |
| `MAXIS-AUTO…` | RM53.90 → RM53.75 | 21 MAR · 21 APR · 21 MAY | none |
| `COWAY (R)` / `COWAY MALAYSIA` | RM20.00 | 13 APR · 01 JUN (date drifts) | none |
| `TIMEDOTCOM` | varies (121.84 / 102.80) | 23 APR · 21 MAY | **Time's own SMS receipt (62003)** |

Grab subscription (user: RM23.90/month) does **not** appear on any of the
three statements → it is not charged to this card. Open question below.

## Shipped 2026-07-07

1. **`TimeParser`** (parser-core, sender `62003`/`TIME`): parses the
   "We've received your payment … Amt: RMx.xx" receipt as an EXPENSE with
   bank name **"UOB"** — deliberately, because the bill auto-charges the
   UOB card and the amount varies month to month (a fixed scheduled entry
   would be wrong). Time A/C number in the body is never extracted (would
   mint a bogus account). Covered by `TimeParserTest` (real masked receipt)
   and pinned in `MalaysianAliasResolutionTest`.
2. **Expense-direction auto-generation** (schema v58, additive
   `subscriptions.auto_generate`, AutoMigration 57→58): an EXPENSE
   subscription with the new "Auto-record on schedule" switch ON is
   materialised by the existing phantom creator
   (`GenerateIncomeAutopayUseCase`) on each due date, catching up missed
   cycles, idempotent via `autopay-<subId>-<date>` hashes. The optional
   "Charged to bank" field (set it to `UOB`) makes phantoms count in the
   UOB cashback tracker. Deleting a phantom EXPENSE does NOT regenerate it
   (that path stays INCOME-only — deletion means "didn't charge this
   cycle").
3. **UOB engine calibration from the APR statement** (see the UOB plan doc
   errata section): rounding is HALF_UP, insurance counts toward min
   spend/Others.

## User setup (one-time, in the app) — 给用户的设置步骤

Add → Subscription → **Expense**，打开 **Auto-record on schedule**，
"Charged to bank" 填 `UOB`，建三条：

| Service Name | Amount | Billing Cycle | Next payment date | Category |
|---|---|---|---|---|
| Great Eastern | 300.00 | Monthly | 下一个月底最后一天 | Insurance（或自选） |
| Maxis | 53.75 | Monthly | 下一个 21 号 | Bills |
| Coway | 20.00 | Monthly | 下一个 1 号（入账日不稳，13 APR/01 JUN 均见过） | Bills |

Time 不用建订阅——SMS 收据自动入账。

Note: monthly advance from the 31st clamps in shorter months (31 MAR →
30 APR → 30 MAY), so the Great Eastern phantom may run a day early vs the
statement line. Harmless for cycle assignment (month-end is mid-window).

## Open questions for the user — 待用户确认

1. **Maxis 金额**：你说每月固定 RM83.90，但三份账单上 `MAXIS-AUTO` 是
   53.90/53.75/53.75。是套餐改了价，还是 RM83.90 里含另一条不走 UOB 卡的
   线路/账单？订阅先按 53.75 建，确认后改金额即可。
2. **Grab subscription RM23.90**：不在 UOB 账单上——从哪里扣
   （GrabPay 余额？别的卡？）？知道渠道才能决定自动化方式。
3. **Great Eastern / Coway / Maxis 扣款时 UOB 完全无 SMS** —— 已按账单
   证据认定成立；若某月真的收到 UOB SMS，请关掉该订阅的 Auto-record
   （否则重复记账）。

## On-device verification checklist

- [ ] 建好三条订阅后跑一次 SMS 扫描 → 出现三笔已到期周期的 UOB 支出。
- [ ] Settings → UOB One Cashback：Others 类 spend 含这三笔。
- [ ] 下次 Time 扣款 SMS 到 → 自动记一笔 "Time Fibre" UOB 支出，金额与
      短信一致。
- [ ] 删除某笔自动生成的支出 → 不会当天重新生成。
