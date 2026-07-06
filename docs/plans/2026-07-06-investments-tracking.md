# Plan: Investment Accounts Tracking (RHB / MooMoo / KWSP / PMO Plus / myASNB)

> Handover plan written 2026-07-06 (Fable 5 session). Third plan; shares the
> manual-account foundation with `2026-07-06-subaccounts-interest-homeloan.md`
> but with a DIFFERENT value model — read the modeling rule first. Also
> resolves that plan's dangling "Ryt Invest → T4.3" reference: Ryt Invest
> belongs HERE.

## Product decision (user request 2026-07-06)

Track five investment platforms inside PennyWise:
1. **RHB Trade Smart** (Bursa stocks)
2. **MooMoo** — holds BOTH USD and MYR (user-confirmed 2026-07-06) →
   **two account rows**: `MooMoo`/`USD` and `MooMoo`/`MYR` (an account row
   carries exactly one currency; never mix — CLAUDE.md money rules)
3. **KWSP / EPF** (retirement; RM250/month contribution ALREADY auto-captured
   via the MAE DuitNow AutoDebit work + subscription)
4. **PMO Plus** (Public Mutual unit trusts)
5. **myASNB** (ASB — fixed RM1 unit price, annual dividend)

**Ryt Invest: dropped** (user 2026-07-06 — not used; do not create it).

**UI decision (user 2026-07-06): Investment is a TOP-LEVEL bottom-nav tab**
— nav order: Home · Analytics · **Investment** · Settings. Not a Settings
sub-screen. Consequences: NavigationBar (phone) AND NavigationRail (tablet)
both gain a tab; register the destination in BOTH navigation systems
(typed `PennyWiseNavHost` + string-route `MainScreen` — review finding M6,
missing one = silent no-op); pick a Material icon (e.g. `TrendingUp`).

This aligns with upstream `docs/planned-features.md` Phase 4 "Net Worth
Tracking" — build fork-first, minimal.

## THE modeling rule (differs from savings sub-accounts — do not mix up)

| | Savings sub-accounts (Jars/GO+/…) | Investment accounts |
|---|---|---|
| Balance goes up by itself | real interest → **INCOME row** via DeriveInterestUseCase | market movement → **snapshot only, NO transaction** |
| Money moved in | TRANSFER | **INVESTMENT-type transaction** (existing bucket, excluded from expense stats by DOMAIN_MODEL §5) |
| Cash payout (dividend/distribution) | n/a | **INCOME row** (category "Dividends") when actually credited |

⚠️ Consequence for the other plan's T1.3: `DeriveInterestUseCase` routing
must apply ONLY to the savings account list, NEVER to accounts marked
investment — a rising portfolio must not fabricate income. Encode the
account sets explicitly (e.g. by `account_type = INVESTMENT`).

## Architecture

- One manual account per platform (`account_balances`, `account_type =
  INVESTMENT`): `RHB Trade Smart`, `MooMoo`, `KWSP`, `PMO Plus`, `myASNB`,
  `Ryt Invest`. Currency MYR except ❓ MooMoo (if USD: keep the account in
  USD; CLAUDE.md money rules forbid mixing — portfolio totals go through
  `sumByCurrency` / existing ExchangeRate conversion like LoansViewModel).
- **Contributions**: the bank-side outflow (FPX to broker etc.) is already
  captured by existing bank parsers as EXPENSE — add Rules (preferred over
  code, DOMAIN_MODEL §6): merchant match → SET type INVESTMENT + category
  "Investments". KWSP monthly RM250 is already captured; just ensure its
  type/category routes to the investment bucket.
- **Valuation**: manual "Update balance" writes a snapshot (source MANUAL).
  Balance history = value-over-time series for charts. No derived rows.
- **Dividends**: ASNB/EPF annual distributions, stock dividends → manual
  INCOME entry (category "Dividends") or captured from bank notification if
  credited to a tracked bank account. EPF dividend compounds inside KWSP →
  it's a balance snapshot bump, NOT income to cash (record as snapshot; only
  withdrawals ever become cash income).
- **UI v1**: an Investments section listing each account: invested (Σ
  INVESTMENT transactions linked to it), current value (latest snapshot),
  unrealized P/L (value − invested), per-currency totals. Value chart from
  balance history. **Account-level only — NO per-holding (per-stock)
  tracking in v1**; that's a different product (symbols, quotes, live
  prices) and violates the effort/benefit line. Revisit only if the user
  asks after using v1.
- **No live market data.** The app is local-first; only exchange rates are
  fetched (existing ExchangeRateDao). Portfolio value updates when the user
  updates it (or, later, screenshot OCR of each app's portfolio page —
  same channel-2 pattern as the wallets plan, one reader per app).

## Tasks

- [ ] **T-I1 Account seeding + type routing** (after the wallets plan's
      T1.4 pattern is proven): create the SIX account rows (RHB, MooMoo-USD,
      MooMoo-MYR, KWSP, PMO Plus, myASNB); verify ManageAccounts supports
      account_type=INVESTMENT creation. Seed values: user enters current
      market values in-app during this task. Exclude INVESTMENT accounts
      from DeriveInterestUseCase routing (add the guard + a unit test).
- [ ] **T-I2 Contribution rules**: per platform, a Rule matching the FPX/
      transfer merchant text → type INVESTMENT, category "Investments".
      Needs one real transfer notification per platform to know the
      merchant text (❓ samples). KWSP: verify the existing RM250 capture
      lands as INVESTMENT (currently probably EXPENSE).
- [ ] **T-I3 Investment TAB v1** (bottom-nav, per the UI decision above):
      new top-level destination in NavigationBar + NavigationRail + both
      nav systems. Screen: account list with invested / current value /
      unrealized P/L, value chart per account (balance history already
      exists). Currency-safe totals: USD and MYR never merged — render
      `RM12,345 · $6,789` style via `formatByCurrency`, or a converted
      single figure through the existing ExchangeRate path (follow
      LoansViewModel's unified-mode precedent).
- [ ] **T-I4 Dividend entry affordance**: quick-add INCOME "Dividends"
      pre-filled from an investment account context.
- [ ] **T-I5 (later) screenshot OCR readers** per platform portfolio page —
      same pattern as wallets channel 2; needs page screenshots as samples.

## Needed from user (给用户)

1. ~~MooMoo 币种~~ ✅ USD + MYR 都有 → 两个账户行。
2. ~~Ryt Invest~~ ✅ 不做。
3. 各平台**当前市值**：T-I1 时直接在 app 里输入即可，不用提前给。
4. 每个平台**下次入金时**银行通知原文复制一条（Rule 商户匹配用，
   用户确认"有了才给"——不阻塞 T-I1/T-I3）。
5. （T-I5 时才需要）各 app 持仓页截图。
