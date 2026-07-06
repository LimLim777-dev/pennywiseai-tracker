package com.pennywiseai.tracker.ui.screens.uob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.service.UobCashbackEngine
import com.pennywiseai.tracker.domain.service.UobCategoryMapper
import com.pennywiseai.tracker.domain.service.UobCycleResult
import com.pennywiseai.tracker.domain.service.UobTxn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class UobCashbackUiState(
    val result: UobCycleResult? = null,
    val daysLeftInCycle: Long = 0,
    val capturedTxnCount: Int = 0,
)

/**
 * Feeds captured UOB card transactions into [UobCashbackEngine] for the
 * CURRENT statement cycle (plan T-C3). Category attribution is
 * merchant-name based via [UobCategoryMapper] — approximate by nature (the
 * SMS carries no MCC); the screen states that the numbers cover captured
 * transactions only (UOB tap-to-pay emits no SMS).
 */
@HiltViewModel
class UobCashbackViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
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

        transactionRepository.getTransactionsBetweenDates(fetchStart, fetchEnd)
            .map { txns ->
                val uobTxns = txns
                    .filter { it.bankName == "UOB" }
                    .filter {
                        it.transactionType == TransactionType.CREDIT ||
                            it.transactionType == TransactionType.EXPENSE
                    }
                    .map {
                        UobTxn(
                            date = it.dateTime.toLocalDate(),
                            amount = it.amount,
                            category = UobCategoryMapper.categorize(it.merchantName),
                        )
                    }
                UobCashbackUiState(
                    result = engine.evaluate(statementMonth, uobTxns),
                    daysLeftInCycle = ChronoUnit.DAYS.between(today, window.endInclusive)
                        .coerceAtLeast(0),
                    capturedTxnCount = uobTxns.size,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UobCashbackUiState(),
            )
    }
}
