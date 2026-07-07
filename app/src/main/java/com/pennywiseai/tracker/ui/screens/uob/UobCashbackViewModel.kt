package com.pennywiseai.tracker.ui.screens.uob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UobOverridesRepository
import com.pennywiseai.tracker.domain.service.UobCashbackEngine
import com.pennywiseai.tracker.domain.service.UobCategoryMapper
import com.pennywiseai.tracker.domain.service.UobCycleResult
import com.pennywiseai.tracker.domain.service.UobRebateCategory
import com.pennywiseai.tracker.domain.service.UobTxn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A merchant's aggregated spend within the current cycle (for reassignment). */
data class UobMerchantSpend(
    val merchant: String,
    val spend: BigDecimal,
    val category: UobRebateCategory,
    val overridden: Boolean,
)

data class UobCashbackUiState(
    val result: UobCycleResult? = null,
    val daysLeftInCycle: Long = 0,
    val capturedTxnCount: Int = 0,
    /** Cycle merchants grouped by their (possibly overridden) category. */
    val merchantsByCategory: Map<UobRebateCategory, List<UobMerchantSpend>> = emptyMap(),
)

/**
 * Feeds captured UOB card transactions into [UobCashbackEngine] for the
 * CURRENT statement cycle (plan T-C3). Category attribution is
 * merchant-name based via [UobCategoryMapper] — approximate by nature (the
 * SMS carries no MCC) — so the user can pin any merchant to a category
 * (plan T-C4); overrides persist via [UobOverridesRepository].
 */
@HiltViewModel
class UobCashbackViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    private val overridesRepository: UobOverridesRepository,
) : ViewModel() {

    private val engine = UobCashbackEngine() // ONE Classic, 18th–17th, lag 2

    val uiState: StateFlow<UobCashbackUiState> = run {
        val today = LocalDate.now()
        val statementMonth = engine.statementMonthFor(today)
        val window = engine.windowFor(statementMonth)
        // Fetch a little before the window start: a transaction dated up to
        // lag days earlier can still post into this cycle.
        val fetchStart = window.start.minusDays(7).atStartOfDay()
        val fetchEnd = window.endInclusive.atTime(23, 59, 59)

        combine(
            transactionRepository.getTransactionsBetweenDates(fetchStart, fetchEnd),
            overridesRepository.overrides,
        ) { txns, overrides ->
            val uobCardTxns = txns
                .filter { it.bankName == "UOB" }
                .filter {
                    it.transactionType == TransactionType.CREDIT ||
                        it.transactionType == TransactionType.EXPENSE
                }
            val uobTxns = uobCardTxns.map {
                UobTxn(
                    date = it.dateTime.toLocalDate(),
                    amount = it.amount,
                    category = UobCategoryMapper.categorize(it.merchantName, overrides),
                )
            }
            val merchants = uobCardTxns
                .groupBy { it.merchantName.trim().uppercase() }
                .map { (merchant, group) ->
                    UobMerchantSpend(
                        merchant = merchant,
                        spend = group.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
                        category = UobCategoryMapper.categorize(merchant, overrides),
                        overridden = merchant in overrides,
                    )
                }
                .sortedByDescending { it.spend }
                .groupBy { it.category }
            UobCashbackUiState(
                result = engine.evaluate(statementMonth, uobTxns),
                daysLeftInCycle = ChronoUnit.DAYS.between(today, window.endInclusive)
                    .coerceAtLeast(0),
                capturedTxnCount = uobTxns.size,
                merchantsByCategory = merchants,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UobCashbackUiState(),
            )
    }

    /** Pin [merchant] to [category] for this and every future cycle. */
    fun reassign(merchant: String, category: UobRebateCategory) {
        overridesRepository.setOverride(merchant, category)
    }

    /** Drop the pin and fall back to keyword mapping. */
    fun clearOverride(merchant: String) {
        overridesRepository.setOverride(merchant, null)
    }
}
