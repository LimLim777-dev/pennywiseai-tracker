# Plan: Payslip PDF Import (salary + EPF auto-recording)

> Handover plan written 2026-07-06 (Fable 5 session). Fourth plan. Depends
> on nothing; feeds the KWSP account from the investments plan
> (`2026-07-06-investments-tracking.md`). Resolves the long-blocked
> STATE.md "salary capture" item via a different channel than bank
> notifications.

## Product decision (user request 2026-07-06)

Import the monthly payslip into PennyWise. **Channel: PDF upload** (chosen
over screenshot OCR — the payslip is already a PDF; embedded text beats
OCR on dense tables; `GPayPdfParser.kt` is the in-repo framework
reference).

## Payslip structure (from 3 real samples: Jan/Apr/Jun 2026)

Employer: Dimerco Express (M) Sdn Bhd. Monthly, pay period = calendar month
(`PAY PERIOD FROM 01/06/2026 TO 30/06/2026`). Three-column table:

- **EARNINGS/INCOME**: `BASIC PAY`, `HAND PHONE`, `TRANSPORT`, plus
  variable lines (`BONUS`, `COMBAT TEAM INCENTIVE`) → `TOTAL`.
- **DEDUCTIONS**: `EMPLOYEE EIS`, `EPF CONTRIBUTION`, `SOCSO CONTRIBUTION
  - EM`, `EMPLOYEE TAX (CP39)`, occasional `EMPLOYEE NEI` → `TOTAL`.
- **EMPLOYER CONTRIBUTIONS**: `EPF`, `SOCSO`, `EIS`.
- **OTHER**: `WORKING DAYS`, `NETT PAY`, leave balances, bases.

⚠️ PII: real payslips carry name, employee ID, I/C number, supervisor.
Test fixtures MUST mask all identity fields (keep labels + amounts only).
Never copy identity values into code/tests/docs (CLAUDE.md rule).

## What an import produces (confirm dialog before saving, like the
screenshot confirm activities)

1. **INCOME "Salary"** = `NETT PAY`, merchant "Dimerco Salary" (or
   user-editable), dated the pay-period end (salary lands ~month end),
   currency MYR, hash `payslip-<periodYYYYMM>` (idempotent — re-importing
   the same month's payslip is a no-op).
2. **Two INVESTMENT transactions into the `KWSP` account** (from the
   investments plan): employee `EPF CONTRIBUTION` + employer `EPF`.
   **Dated the 15th of the FOLLOWING month** — user-confirmed that KWSP
   only reflects payroll EPF on the 12th–16th of the next month; dating
   them mid-window keeps the KWSP invested-total roughly in sync with its
   balance snapshots. Hashes `payslip-epf-ee-<period>` /
   `payslip-epf-er-<period>`. (The separate RM250/month DuitNow AutoDebit
   voluntary contribution is already captured elsewhere — do NOT merge.)
3. Tax / SOCSO / EIS / NEI: **no transactions** (pre-net deductions that
   never touch an account). Keep the parsed breakdown in the salary
   transaction's description (e.g. "Gross 5,393.00 · EPF 594 · Tax 103.60")
   so the detail screen shows it.

**Salary credit date (user-confirmed 2026-07-06)**: last working day of
the month; if it falls on Sat/Sun/public holiday → one day earlier.
**Maybank emits NO push notification for the credit** → the payslip import
is permanently the sole salary record; no cross-dedup ever needed. Default
the INCOME date to the last weekday of the pay-period month (public
holidays can't be computed reliably per-state — the confirm dialog's
editable date covers those cases).

**Salary-month vs spending-month (user question answered 2026-07-06)**:
salary lands end of month M but funds month M+1. Decision: transactions
KEEP their real dates (balance history depends on it — never shift data).
The pairing problem is solved at the VIEW layer: a "financial month start
day" setting for Home/Analytics aggregation windows (e.g. month = 28th to
27th). Upstream TODO.md has the same request for credit-card cycles, and
the UOB cashback plan's cycle engine (T-C2) is the same window abstraction
— build once, use for both. Tracked as T-P4 below.

## Tasks

- [ ] **T-P1 `PayslipPdfParser`** (pure text→struct function + JUnit tests
      from the 3 masked fixtures; follow `GPayPdfParser.kt` for PDF-text
      extraction plumbing). Must tolerate variable earnings lines
      (bonus/incentive months) and the occasional extra deduction line.
- [ ] **T-P2 Import flow**: Settings (or the Investment tab) entry →
      system file picker (PDF) → parse → confirm dialog (salary +
      2 EPF rows, all editable) → insert with idempotent hashes.
- [ ] **T-P3 Salary history view (optional v2)**: monthly gross/net/
      deductions table from imported payslips — decide with user after v1;
      would need a small `payslips` table (W6, additive) since v1 stores
      only the derived transactions.
- [ ] **T-P4 Financial-month view setting** (Analytics feature, shared
      abstraction with the UOB cycle engine): user-set month start day for
      Home/Analytics aggregations so end-of-month salary pairs with the
      month it funds. View-layer only — never shifts transaction dates.
      **Scoping done 2026-07-08** — month-window sites that must all
      honor the setting (grep `withDayOfMonth(1)`): `HomeViewModel` lines
      ~322/425/448/477/514/533/1058 (current-month spend, income, last
      month comparison) and `AnalyticsViewModel` ~506/526–528 (trend
      month loop); check budget aggregation too. Plan of attack: (1) pure
      `FinancialMonth(startDay)` domain class + JVM tests (window-for-date,
      clamping in short months — reuse UobCashbackEngine.windowFor
      semantics), (2) DataStore pref `financialMonthStartDay` default 1,
      (3) replace each site with the abstraction, (4) label windows in
      the UI ("25 Jun – 24 Jul") so the user sees which month they're in.
      Do this EARLY in a session — it touches every money-aggregation
      path and needs the full regression suite + an on-device sanity pass.

## Fixture source (T-P1 — COLLECTED 2026-07-06; extraction tool added 2026-07-07)

Three real payslip PDFs exist on the user's machine:
`D:\Downloads\ME 202601 PENE M33.pdf` (bonus month) ·
`ME 202604 PENE M33.pdf` (incentive month) ·
`ME 202606 PENE M33.pdf` (plain month).
**Do NOT commit the PDFs** — they contain identity data (name, I/C, employee
id).

**T-P1 fixture procedure (tooling now exists)**: the app has Settings →
**PDF Text Debug** — pick a PDF, it shows the RUNTIME PDFBox
(`PdfTextExtractor`) output, selectable/copyable. The extraction order of
the 3-column payslip table is only knowable from this real output — never
write the parser regexes from the visual layout. Ask the user to run each
payslip through it and paste the three texts back; **mask the identity
fields** (name, I/C, employee id, supervisor — keep labels + amounts) and
commit only the masked texts as fixtures. Then implement
`PayslipPdfParser : PdfStatementParser` (`canHandle` = employer name +
PAY PERIOD markers) with the fixtures as tests, following
`GPayPdfParserTest`'s text-fixture style.

## Needed from user (给用户)

~~全部收齐~~ ✅（PDF ×3、到账日规则、无推送通知）。T-P1 可直接开工。
