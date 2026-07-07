package com.pennywiseai.tracker.navigation

import kotlinx.serialization.Serializable

// Define navigation destinations using Kotlin Serialization
@Serializable
object AppLock

@Serializable
object OnBoarding

@Serializable
object Permission

@Serializable
object Home

@Serializable
object Transactions

@Serializable
object Settings

@Serializable
object Categories

@Serializable
object Analytics

@Serializable
object Chat

@Serializable
data class TransactionDetail(val transactionId: Long)

// Optional prefill fields (all defaulted — callers pass only what they know):
// used by contextual shortcuts like the Investment tab's "Record dividend".
// Property names are the AddViewModel SavedStateHandle keys.
@Serializable
data class AddTransaction(
    val sourceTransactionId: Long? = null,
    val prefillType: String? = null,
    val prefillCategory: String? = null,
    val prefillMerchant: String? = null,
    val prefillBankName: String? = null,
    val prefillAccountLast4: String? = null,
)

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String)

@Serializable
object UnrecognizedSms

@Serializable
object Faq

@Serializable
object Rules

@Serializable
data class CreateRule(val ruleId: String? = null, val duplicateFromId: String? = null)

@Serializable
object ExchangeRates

@Serializable
object BudgetGroups

@Serializable
data class BudgetGroupEdit(val groupId: Long = -1L)

@Serializable
object Loans

@Serializable
data class LoanDetail(val loanId: Long)

@Serializable
object TransactionGroups

@Serializable
data class TransactionGroupDetail(val groupId: Long)

@Serializable
object ImportStatement

@Serializable
object NotificationLog

@Serializable
data class TransactionsWithFilter(
    val category: String,
    val period: String? = null,
    val currency: String? = null
)
@Serializable
object UobCashback

@Serializable
object ManageAccounts

@Serializable
object AddAccount

// Property names must stay bankName/accountLast4 — BalanceHistoryViewModel
// reads them from SavedStateHandle by these keys.
@Serializable
data class BalanceHistory(val bankName: String, val accountLast4: String)

@Serializable
object Appearance
