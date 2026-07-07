package com.pennywiseai.tracker.presentation.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InvestmentUiState(
    val rows: List<InvestmentRow> = emptyList(),
    /** Latest snapshot values, bucketed per currency — never merged across. */
    val totalValueByCurrency: Map<String, Money> = emptyMap(),
)

/**
 * Investment tab v1 (plan T-I3): lists accounts with account_type
 * INVESTMENT — current value from the latest manual snapshot, invested from
 * captured INVESTMENT transactions, unrealized P/L when both are known.
 */
@HiltViewModel
class InvestmentViewModel @Inject constructor(
    accountBalanceRepository: AccountBalanceRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<InvestmentUiState> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        transactionRepository.getTransactionsByType(TransactionType.INVESTMENT),
    ) { balances, investmentTxns ->
        val accounts = balances.filter { it.accountType == "INVESTMENT" }
        val rows = buildInvestmentRows(accounts, investmentTxns)
        InvestmentUiState(
            rows = rows,
            totalValueByCurrency = rows.sumByCurrency({ it.currency }, { it.currentValue }),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvestmentUiState(),
    )
}
