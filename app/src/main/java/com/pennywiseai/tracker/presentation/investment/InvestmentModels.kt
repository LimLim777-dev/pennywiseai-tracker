package com.pennywiseai.tracker.presentation.investment

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal

/**
 * One row on the Investment tab. The value model (plan
 * 2026-07-06-investments-tracking.md) differs from savings accounts:
 * market movement is snapshot-only, contributions are INVESTMENT-type
 * transactions, and unrealized P/L is only computable when at least one
 * contribution has been attributed to the account.
 */
data class InvestmentRow(
    val bankName: String,
    val accountLast4: String,
    val currency: String,
    val currentValue: BigDecimal,
    /** Σ INVESTMENT transactions attributed to this account; null = none captured yet. */
    val invested: BigDecimal?,
    /** currentValue − invested; null while invested is unknown. */
    val unrealizedPl: BigDecimal?,
)

/**
 * Builds Investment-tab rows from the latest balances and the INVESTMENT
 * transaction set. Attribution is by (bankName, currency): contributions
 * recorded against the platform's name in the matching currency count as
 * invested capital. Deliberately conservative — a platform with no matching
 * transactions shows value only (no fabricated cost basis), and amounts in
 * a different currency never mix into the account's figures.
 */
fun buildInvestmentRows(
    accounts: List<AccountBalanceEntity>,
    investmentTransactions: List<TransactionEntity>,
): List<InvestmentRow> = accounts.map { account ->
    val contributions = investmentTransactions.filter {
        it.bankName.equals(account.bankName, ignoreCase = true) &&
            it.currency == account.currency
    }
    val invested = if (contributions.isEmpty()) null
    else contributions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
    InvestmentRow(
        bankName = account.bankName,
        accountLast4 = account.accountLast4,
        currency = account.currency,
        currentValue = account.balance,
        invested = invested,
        unrealizedPl = invested?.let { account.balance - it },
    )
}
