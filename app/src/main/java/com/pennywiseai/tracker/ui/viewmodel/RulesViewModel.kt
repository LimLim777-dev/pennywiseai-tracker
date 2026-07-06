package com.pennywiseai.tracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.FreeTierLimits
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.worker.DailyIncomeWorker
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleTemplateService
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import com.pennywiseai.tracker.domain.usecase.InitializeRuleTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleRepository: RuleRepository,
    private val ruleTemplateService: RuleTemplateService,
    private val initializeRuleTemplatesUseCase: InitializeRuleTemplatesUseCase,
    private val applyRulesToPastTransactionsUseCase: ApplyRulesToPastTransactionsUseCase,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    entitlementGate: EntitlementGate,
) : ViewModel() {

    /**
     * Pro entitlement. Drives both the "create another rule" gate below
     * and the inline quota caption on the Rules screen.
     */
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _batchApplyProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchApplyProgress: StateFlow<Pair<Int, Int>?> = _batchApplyProgress.asStateFlow()

    private val _batchApplyResult = MutableStateFlow<BatchApplyResult?>(null)
    val batchApplyResult: StateFlow<BatchApplyResult?> = _batchApplyResult.asStateFlow()

    private val _dryRunResult = MutableStateFlow<DryRunResult?>(null)
    val dryRunResult: StateFlow<DryRunResult?> = _dryRunResult.asStateFlow()

    val rules: StateFlow<List<TransactionRule>> = ruleRepository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * True when the user is allowed to create another rule — either Pro
     * (unlimited) or under [FreeTierLimits.MAX_RULES]. The Rules screen's
     * FAB checks this to decide between opening the create flow and
     * triggering the paywall.
     */
    val canCreateMoreRules: StateFlow<Boolean> = combine(rules, isProEntitled) { all, pro ->
        pro || all.size < FreeTierLimits.MAX_RULES
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    /**
     * Non-hidden accounts from `hidden_accounts` SharedPreferences key.
     */
    val accounts: StateFlow<List<AccountInfo>> = accountBalanceRepository.getAllLatestBalances()
        .map { balances ->
            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            balances
                .filter { balance ->
                    val key = "${balance.bankName}_${balance.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                .map { balance ->
                    AccountInfo(
                        bankName = balance.bankName,
                        accountLast4 = balance.accountLast4,
                        displayName = "${balance.bankName} ••${balance.accountLast4}",
                        isCreditCard = balance.isCreditCard,
                        accountType = balance.accountType
                    )
                }
                .distinctBy { "${it.bankName}_${it.accountLast4}" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeRules()
    }

    private fun initializeRules() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deletedIds = sharedPrefs.getStringSet("deleted_system_templates", emptySet()) ?: emptySet()
                initializeRuleTemplatesUseCase(deletedSystemTemplateIds = deletedIds)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleRule(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                ruleRepository.setRuleActive(ruleId, isActive)
                val rule = ruleRepository.getRuleById(ruleId) ?: return@launch
                rule.actions
                    .filter { it.actionType == ActionType.GENERATE_DAILY_INCOME }
                    .forEach { action ->
                        val parts = action.value.split("|")
                        val merchant = parts.getOrNull(0) ?: return@forEach
                        val amount = parts.getOrNull(1)?.toBigDecimalOrNull() ?: return@forEach
                        val timeHHmm = parts.getOrNull(2) ?: "09:00"
                        if (isActive) {
                            subscriptionRepository.createDailyIncomeSubscription(merchant, amount, ruleId)
                            scheduleDailyIncomeWork(ruleId, timeHHmm)
                        } else {
                            subscriptionRepository.endSubscriptionByRuleId(ruleId)
                            cancelDailyIncomeWork(ruleId)
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            }
        }
    }

    fun updateScheduleTime(ruleId: String, timeHHmm: String) {
        viewModelScope.launch {
            try {
                val rule = ruleRepository.getRuleById(ruleId) ?: return@launch
                val updatedActions = rule.actions.map { action ->
                    if (action.actionType == ActionType.GENERATE_DAILY_INCOME) {
                        val parts = action.value.split("|")
                        val merchant = parts.getOrNull(0) ?: return@map action
                        val amount = parts.getOrNull(1) ?: return@map action
                        action.copy(value = "$merchant|$amount|$timeHHmm")
                    } else action
                }
                ruleRepository.updateRule(rule.copy(actions = updatedActions))
                if (rule.isActive) {
                    scheduleDailyIncomeWork(ruleId, timeHHmm)
                }
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            }
        }
    }

    private fun scheduleDailyIncomeWork(ruleId: String, timeHHmm: String) {
        val workName = "${DailyIncomeWorker.WORK_NAME_PREFIX}$ruleId"
        val target = LocalTime.parse(timeHHmm)
        val now = ZonedDateTime.now()
        var next = now.withHour(target.hour).withMinute(target.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val initialDelay = next.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

        val request = PeriodicWorkRequestBuilder<DailyIncomeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(workName)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelDailyIncomeWork(ruleId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("${DailyIncomeWorker.WORK_NAME_PREFIX}$ruleId")
    }

    fun createRule(rule: TransactionRule) {
        viewModelScope.launch {
            try {
                ruleRepository.insertRule(rule)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                val rule = ruleRepository.getRuleById(ruleId)
                if (rule?.isSystemTemplate == true) {
                    val deleted = sharedPrefs.getStringSet("deleted_system_templates", emptySet())!!.toMutableSet()
                    deleted.add(ruleId)
                    sharedPrefs.edit().putStringSet("deleted_system_templates", deleted).apply()
                }
                // Deleting a daily-income rule must also stop its worker and end its
                // subscription — otherwise phantom income keeps generating with no
                // visible rule left to turn off.
                if (rule?.actions?.any { it.actionType == ActionType.GENERATE_DAILY_INCOME } == true) {
                    subscriptionRepository.endSubscriptionByRuleId(ruleId)
                    cancelDailyIncomeWork(ruleId)
                }
                ruleRepository.deleteRule(ruleId)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            }
        }
    }

    fun updateRule(rule: TransactionRule) {
        viewModelScope.launch {
            try {
                ruleRepository.updateRule(rule)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            }
        }
    }

    fun getRuleApplicationCount(ruleId: String): Flow<Int> = flow {
        emit(ruleRepository.getRuleApplicationCount(ruleId))
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                sharedPrefs.edit().remove("deleted_system_templates").apply()
                initializeRuleTemplatesUseCase(forceReset = true)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun applyRuleToPastTransactions(
        rule: TransactionRule,
        applyToUncategorizedOnly: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _batchApplyProgress.value = 0 to 0
            _batchApplyResult.value = null

            try {
                val result = if (applyToUncategorizedOnly) {
                    applyRulesToPastTransactionsUseCase.applyRuleToUncategorizedTransactions(
                        rule = rule,
                        onProgress = { processed, total ->
                            _batchApplyProgress.value = processed to total
                        }
                    )
                } else {
                    applyRulesToPastTransactionsUseCase.applyRuleToAllTransactions(
                        rule = rule,
                        onProgress = { processed, total ->
                            _batchApplyProgress.value = processed to total
                        }
                    )
                }
                _batchApplyResult.value = result
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
                _batchApplyResult.value = BatchApplyResult(
                    totalProcessed = 0,
                    totalUpdated = 0,
                    errors = listOf("Error: ${e.message}")
                )
            } finally {
                _isLoading.value = false
                _batchApplyProgress.value = null
            }
        }
    }

    fun applyAllRulesToPastTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            _batchApplyProgress.value = 0 to 0
            _batchApplyResult.value = null

            try {
                val result = applyRulesToPastTransactionsUseCase.applyAllActiveRulesToTransactions(
                    onProgress = { processed, total ->
                        _batchApplyProgress.value = processed to total
                    }
                )
                _batchApplyResult.value = result
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
                _batchApplyResult.value = BatchApplyResult(
                    totalProcessed = 0,
                    totalUpdated = 0,
                    errors = listOf("Error: ${e.message}")
                )
            } finally {
                _isLoading.value = false
                _batchApplyProgress.value = null
            }
        }
    }

    fun clearBatchApplyResult() {
        _batchApplyResult.value = null
        _dryRunResult.value = null
    }

    fun previewRule(rule: TransactionRule) {
        viewModelScope.launch {
            _isLoading.value = true
            _dryRunResult.value = null
            try {
                _dryRunResult.value = applyRulesToPastTransactionsUseCase.previewRuleApplication(rule)
            } catch (e: Exception) {
                android.util.Log.e("RulesViewModel", "operation failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * UI model for an account in the rule condition picker
     */
    data class AccountInfo(
        val bankName: String,
        val accountLast4: String,
        val displayName: String,
        val isCreditCard: Boolean,
        val accountType: String?
    )
}