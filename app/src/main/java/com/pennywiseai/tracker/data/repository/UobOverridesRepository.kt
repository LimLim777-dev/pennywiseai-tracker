package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.domain.service.UobRebateCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-merchant category overrides for the UOB cashback tracker (plan T-C4).
 * Keyword mapping is approximate (SMS carries no MCC); a wrong guess makes
 * the rebate estimate drift every month, so the user can pin a merchant to a
 * category once and it sticks.
 *
 * Stored in the `account_prefs` SharedPreferences file as
 * "MERCHANT|CATEGORY" entries so it rides the existing account_ui backup
 * section (an override set is exactly the kind of state a restore on a new
 * phone must bring back).
 */
@Singleton
class UobOverridesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<String, UobRebateCategory>> = _overrides.asStateFlow()

    private fun load(): Map<String, UobRebateCategory> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val category = runCatching { UobRebateCategory.valueOf(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            parts[0] to category
        }.toMap()

    /** Pin [merchant] to [category]; null removes the override. */
    fun setOverride(merchant: String, category: UobRebateCategory?) {
        val key = merchant.trim().uppercase()
        val updated = load().toMutableMap()
        if (category == null) updated.remove(key) else updated[key] = category
        prefs.edit()
            .putStringSet(KEY, updated.map { (m, c) -> "$m|${c.name}" }.toSet())
            .apply()
        _overrides.value = updated
    }

    companion object {
        const val KEY = "uob_category_overrides"
    }
}
