package com.pennywiseai.tracker.domain.model.rule

import kotlinx.serialization.Serializable

@Serializable
data class RuleAction(
    val field: TransactionField,
    val actionType: ActionType,
    val value: String
) {
    fun validate(): Boolean {
        return when (actionType) {
            ActionType.SET -> value.isNotBlank()
            ActionType.APPEND, ActionType.PREPEND -> value.isNotBlank()
            ActionType.CLEAR -> true
            ActionType.ADD_TAG -> value.isNotBlank()
            ActionType.REMOVE_TAG -> value.isNotBlank()
            ActionType.BLOCK -> true
            ActionType.GENERATE_DAILY_INCOME -> value.isNotBlank()
            ActionType.EXCLUDE_FROM_ANALYTICS -> true
        }
    }
}

@Serializable
enum class ActionType {
    SET,
    APPEND,
    PREPEND,
    CLEAR,
    ADD_TAG,
    REMOVE_TAG,
    BLOCK,
    GENERATE_DAILY_INCOME,  // value = "merchantName|amount"; creates a Daily INCOME subscription when rule is toggled ON
    EXCLUDE_FROM_ANALYTICS  // marks transaction as excluded from income/expense analytics
}