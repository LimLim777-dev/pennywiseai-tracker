package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pure decision core for interest derivation (subaccounts plan T1.2) —
 * kept free of repositories so every money-semantics branch is a JVM test.
 */
sealed interface InterestDecision {
    /** Observed > derived: record the delta as INCOME "Interest". */
    data class RecordInterest(val amount: BigDecimal) : InterestDecision

    /**
     * Observed < derived: NEVER fabricate a row — surface the (positive)
     * shortfall so the user can add the missing fee/withdrawal.
     */
    data class Shortfall(val amount: BigDecimal) : InterestDecision

    data object NoChange : InterestDecision

    /** Non-manual, credit-card, or INVESTMENT account — derivation must not run. */
    data object NotEligible : InterestDecision

    /** An interest row for this account+date already exists (idempotency). */
    data object AlreadyRecordedToday : InterestDecision
}

/**
 * Decides what a balance observation means for a savings-style manual
 * account. INVESTMENT accounts are hard-excluded: a rising portfolio is
 * market movement, never income (investments plan modeling rule).
 *
 * Transfers into the account the same day are already inside
 * [derivedBalance] (the repository's signed sum includes transfer legs),
 * so they can never be mistaken for interest here.
 */
fun decideInterest(
    isManualAccount: Boolean,
    accountType: String?,
    isCreditCard: Boolean,
    derivedBalance: BigDecimal,
    observedBalance: BigDecimal,
    alreadyRecordedToday: Boolean,
): InterestDecision {
    if (!isManualAccount || isCreditCard || accountType == "INVESTMENT") {
        return InterestDecision.NotEligible
    }
    val delta = observedBalance - derivedBalance
    return when {
        delta.signum() == 0 -> InterestDecision.NoChange
        delta.signum() < 0 -> InterestDecision.Shortfall(delta.negate())
        alreadyRecordedToday -> InterestDecision.AlreadyRecordedToday
        else -> InterestDecision.RecordInterest(delta)
    }
}

/** Idempotent hash: one derived-interest row per account per day. */
fun interestHash(bankName: String, accountLast4: String, date: LocalDate): String =
    "interest-$bankName-$accountLast4-$date"

/**
 * Turns a manual balance observation into books that match reality
 * (subaccounts plan, "Interest derivation" architecture):
 * derived = opening + Σ(signed transactions incl. transfer legs);
 * a positive delta becomes one idempotent INCOME "Interest" row and the
 * balance is recomputed to the observation. A negative delta is returned
 * for the UI to resolve — nothing is fabricated.
 */
class DeriveInterestUseCase @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        bankName: String,
        accountLast4: String,
        observedBalance: BigDecimal,
        observedDate: LocalDate = LocalDate.now(),
    ): InterestDecision {
        val latest = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
            ?: return InterestDecision.NotEligible
        val derived = accountBalanceRepository.derivedManualBalance(bankName, accountLast4)
            ?: return InterestDecision.NotEligible

        val hash = interestHash(bankName, accountLast4, observedDate)
        val decision = decideInterest(
            isManualAccount = true, // derivedManualBalance already gated on it
            accountType = latest.accountType,
            isCreditCard = latest.isCreditCard,
            derivedBalance = derived,
            observedBalance = observedBalance,
            alreadyRecordedToday = transactionRepository.getTransactionByHash(hash) != null,
        )

        if (decision is InterestDecision.RecordInterest) {
            transactionRepository.insertTransaction(
                TransactionEntity(
                    amount = decision.amount,
                    merchantName = latest.alias ?: bankName,
                    category = "Interest",
                    transactionType = TransactionType.INCOME,
                    dateTime = observedDate.atStartOfDay(),
                    description = "Interest (derived from balance update)",
                    bankName = bankName,
                    accountNumber = accountLast4,
                    currency = latest.currency,
                    transactionHash = hash,
                )
            )
            // Balance now equals the observation: opening + Σ(txns incl. the new row).
            accountBalanceRepository.recomputeManualBalance(bankName, accountLast4)
        }
        return decision
    }
}
