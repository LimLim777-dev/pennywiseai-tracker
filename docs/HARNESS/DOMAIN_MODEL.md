# Domain Model — PennyWise Malaysia Fork

> Harness doc B. The authoritative description of what the data *means*.
> Code is the source of truth for *shape*; this doc is the source of truth for
> *semantics and invariants*. If they disagree, flag it — don't silently pick one.

## 0. The ingestion pipeline (context for everything below)

```
SMS  ──► SmsBroadcastReceiver / OptimizedSmsReaderWorker ─┐
                                                          ├─► BankParserFactory.getParser(sender)
Bank app notification ──► BankNotificationListenerService ┘        │
     (whitelist: receiver/BankNotificationConfig.kt)               ▼
                                                    BankParser.parse() → ParsedTransaction
                                                                   │
                                    SmsTransactionProcessor (dedup, mapping, RuleEngine)
                                                                   ▼
                                              TransactionEntity → Room (single source of truth)
                                                                   ▼
                                         Repositories → ViewModels → Compose UI / Analytics
```

Malaysian channels: **notifications** for Boost, Maybank MAE, GXBank,
Public Bank, TNG, Ryt Bank, ShopeePay (screenshot-assisted); **SMS** only for
UOB credit card (senders 67425, 66300). **Screenshot OCR path** (added
2026-07): `receiver/ScreenshotObserver.kt` watches MediaStore and tries
`ShopeePayScreenshotParser` then `TNGScreenshotParser`; a hit posts a
notification opening `ShopeePayConfirmActivity` / `TNGConfirmActivity`
(amount/merchant editable, rules ARE applied on save with empty smsText —
so SMS_TEXT rule conditions never match screenshot transactions; the path
currently bypasses cross-channel dedup and merchant mapping, review M-R1).

---

## 1. Transaction

**Code**: `app/.../data/database/entity/TransactionEntity.kt` (table `transactions`).
Parser-side precursor: `parser-core/.../ParsedTransaction.kt`.

**Definition**: one real money event observed from a message, screenshot, or
manual entry. It is a *record of what happened*, never a plan or a reminder.

**Type semantics** (`TransactionType`) — these drive all analytics math:

| Type | Meaning | Counted in monthly income/expense? |
|---|---|---|
| `INCOME` | money in from outside | yes (income) |
| `EXPENSE` | money out to outside | yes (expense) |
| `CREDIT` | credit-card purchase (UOB) | yes (expense side) |
| `TRANSFER` | between the owner's own accounts | **no** — excluded so self-transfers never inflate totals |
| `INVESTMENT` | into investments | separate bucket |

**Constraints / invariants**:
- `transactionHash` is UNIQUE (DB index) — the primary dedup mechanism.
  Never change how it is computed without a migration plan for existing rows.
- `currency` defaults to `"INR"` at the entity level. **Every Malaysian write
  path must carry `MYR` explicitly**, which parsers guarantee via
  `getCurrency() = "MYR"`. Never sum across currencies (see `utils/MoneyTotals.kt`
  and the two rules in `CLAUDE.md`).
- `amount` is `BigDecimal`, always positive; direction comes from `transactionType`.
- `excludedFromAnalytics = true` keeps the row in history and balances but out
  of all stats — the user-facing escape hatch for misclassified rows.
- `isDeleted` is a soft delete **that also renames `transactionHash` to
  `DELETED_<id>_<hash>`** (frees the hash so the same details can be re-added
  manually). A user-initiated delete simultaneously writes the ORIGINAL hash
  into the `deleted_transaction_hashes` **tombstone table** (v57, review C1);
  the ingestion pipeline skips any parsed message whose hash is tombstoned,
  so a full SMS rescan never resurrects user-deleted transactions. Manual
  adds use different hash schemes and are unaffected. Tombstones are
  included in backups (all privacy modes — opaque hashes, no PII). Hard
  deletes DO exist and intentionally do NOT tombstone:
  `deleteUncuratedTransactions` (full rescan rebuild, #401) and
  `deleteAllTransactions` exist to re-import.
- Every field serialized into backups must keep a Kotlin default value
  (backup compatibility contract — `docs/backup-format.md`, enforced by
  `BackupSchemaGuardTest`).

**Lifecycle**: captured (parsed / manual) → possibly rewritten by `RuleEngine`
→ inserted (hash-dedup) → user edits (category, note, exclude-flag, loan link,
split) → soft-deleted at most.

**Edge cases specific to this fork**:
1. **Self-transfer, name visible** (`SelfTransferDetector`, owner
   `MAH GUO REN`): counterparty name matches owner → type forced to `TRANSFER`.
2. **Self-transfer, name missing** (Boost incoming): app-layer reclassification
   in `BankNotificationListenerService` — if another bank logged a `TRANSFER`
   of the same amount within ±2 min, the Boost `INCOME` becomes `TRANSFER`.
3. **Wallet-internal moves** (Boost Jar, TNG GO+, Shopee Money+): *not
   transactions at all* — parsers must return `null`. Money never left the owner.
4. **SMS + notification for the same event**: ±2-minute same-amount same-bank
   window in the listener suppresses the duplicate.
5. **Invisible spending** (UOB tap-to-pay, some ShopeePay): no signal exists;
   filled by PDF statement import (future) or manual entry. Do not attempt to
   "infer" these.

**Relationships**: → `Category` (by name string), → `Budget` (via
`budgetCategory`/`budgetImpactType`), → `Loan` (`loanId`), → splits/groups/profile.

---

## 2. Category

**Code**: `CategoryEntity.kt` (table `categories`), name is UNIQUE.

**Definition**: a spending/income label. Transactions store the category **by
name string**, not by foreign key.

**Constraints**:
- Renaming a category does *not* cascade to transactions — a rename is
  actually "create new + reassign + hide old" and existing rows keep the old
  string. Treat category renames as risky.
- `isSystem = true` rows are seeded; do not delete them programmatically.
- `isIncome` splits categories between the income and expense pickers.

**Fork usage**: EPF and insurance categories double as **tax-relief markers**
(planned feature). Prefer creating a `Rule` (see §6) over hardcoding
merchant→category mappings in parsers; `MerchantMappingEntity` handles
display-name renames.

**Lifecycle**: seeded system set → user additions → soft-hide. Effectively
append-only.

---

## 3. Budget

**Code**: `BudgetEntity.kt`, `BudgetCategoryEntity.kt`, month-snapshot entities.

**Definition**: a spending limit (`groupType = LIMIT`), target (`TARGET`), or
expectation (`EXPECTED`) over a period (`WEEKLY`/`MONTHLY`/`CUSTOM`) for a set
of categories (or `includeAllCategories`).

**Constraints**:
- Budget math consumes only `EXPENSE`/`CREDIT` transactions that are not
  excluded-from-analytics; `TRANSFER` never touches budgets.
- Single-currency per budget (`currency` column); mixed-currency aggregation is
  forbidden (CLAUDE.md money rules).
- Month snapshots (`Budget*MonthSnapshotEntity`) freeze history — never
  recompute past months from live data.
- A transaction can override its budget effect via `budgetCategory` +
  `budgetImpactType` (`DEDUCT_SPENT` / `ADD_TO_LIMIT`).

**Fork note**: user's baseline spend is ~RM1200–1500/month ex-one-offs — the
seed for "suggest budgets from history" (planned, not built).

**Lifecycle**: created → active accumulation per period → snapshot at period
close → `isActive = false` to retire.

---

## 4. Recurrence (Subscriptions)

**Code**: `SubscriptionEntity.kt` (table `subscriptions`). This **is** the
recurrence model — do not build a second one.

**Definition**: an expected repeating money event: `direction = EXPENSE`
(Netflix, EPF, insurance) or `INCOME` (salary).

**Behavioral contract** (asymmetric by design):
- **EXPENSE direction**: waits for a real captured transaction and reconciles
  ("paid this cycle" via `lastPaidAt`); it does **not** fabricate transactions.
- **INCOME direction**: *phantom-creates* a transaction when `nextPaymentDate`
  passes (issue #371 mechanism, `worker/DailyIncomeWorker.kt`) — insurance
  against silent salary credits.

**Constraints**:
- `billingCycle` display string ("Daily"/"Weekly"/"Monthly"/"Quarterly"/
  "Semi-Annual"/"Annual") drives date arithmetic in
  `SubscriptionRepository.advance`. Only those exact strings are valid;
  unknown strings fall back to Monthly. ("Daily" exists for the
  GENERATE_DAILY_INCOME rule flow, e.g. MAE Tabung bonus.)
- `lastPaidAt` guards against double mark-as-paid in one cycle (#412) on the
  manual mark-paid path. The SMS-charge auto-advance path
  (`updateNextPaymentDateAfterCharge`) is instead protected by ordering: it
  runs only after the transaction insert is confirmed, inside the same Room
  transaction (2026-07-06, review C3) — duplicates never advance the date.
- Charge matching tolerance: merchant exact (case-insensitive) + amount
  within **±5%**; only EXPENSE/CREDIT transactions can match (refunds never
  advance a subscription).
- States: `ACTIVE` → `HIDDEN` (soft, can auto-reactivate) → `ENDED` (never
  auto-reactivates).
- **Daily-income rules** (`GENERATE_DAILY_INCOME` action): toggling ON
  creates a Daily INCOME subscription (keyed by ruleId) + a periodic worker;
  toggling OFF or deleting the rule ends both. Deleting an `autopay-*`
  phantom transaction rewinds `nextPaymentDate` to today and regenerates
  immediately (after the delete, so the freed hash allows same-day
  re-creation).

**Fork data**: Maxis RM83.75/mo (~21st), EPF RM250/mo (1st),
GreatEastern RM300/mo (1st). Salary: amount/signal unknown → blocked on user
input (see FAILURE_PLAYBOOK §escalation).

**Relationships**: reconciles against `Transaction` by merchant/amount/date
proximity; categories by name.

---

## 5. Analytics

**Code**: no single entity — computed in ViewModels/repositories from Room
flows (`presentation/home/HomeViewModel.kt`, transactions/budgets screens).

**Definition**: derived, never stored (except budget month snapshots). Inputs
are filtered by the invariants above.

**Deterministic filter chain — any analytics change must preserve this order**:
1. drop `isDeleted`
2. drop `excludedFromAnalytics`
3. drop `TRANSFER` (and treat `INVESTMENT` separately)
4. group by `currency` — never merge (single-figure totals only after
   filtering/converting to one currency)
5. then aggregate (by month / category / merchant)

**Edge cases**: mixed-currency rows render as `RM1,250 · $600` style via
`CurrencyFormatter.formatByCurrency`; historical import (401 migrated
Money Manager rows) is already deduped — re-imports must respect
`transactionHash`.

---

## 6. Rules (supporting model — prefer over code changes)

**Code**: `domain/service/RuleEngine.kt`, `RuleEntity`, `RuleApplicationEntity`,
`domain/model/rule/*`.

Priority-ordered, user-editable condition→action rewrites applied at ingestion
(rename merchant, set category, change type, etc.), with an audit trail in
`rule_applications`. **When the user asks for "always categorize X as Y" or
"rename this merchant", the first question is whether a Rule or a
`MerchantMappingEntity` row can do it — a parser/code change is the last resort.**

---

## Requires human judgment (permanently)

- Whether a new message format is a real transaction vs noise (needs a sample).
- Whether an unmatched incoming amount is income or a self-transfer, when no
  signal disambiguates.
- Category taxonomy choices and budget amounts.
- Anything relying on the owner's identity or account list.
