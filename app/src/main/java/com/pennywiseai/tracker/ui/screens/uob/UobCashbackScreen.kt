package com.pennywiseai.tracker.ui.screens.uob

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.domain.service.UobCategoryResult
import com.pennywiseai.tracker.domain.service.UobRebateCategory
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UobCashbackScreen(
    onNavigateBack: () -> Unit,
    onAddTransaction: () -> Unit = {},
    viewModel: UobCashbackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val result = state.result
    var reassignTarget by remember { mutableStateOf<UobMerchantSpend?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UOB One Cashback") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (result == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Cycle header + minimum-spend progress ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${result.window.start.format(dateFmt)} – ${result.window.endInclusive.format(dateFmt)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "${state.daysLeftInCycle} days left",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val threshold = BigDecimal("800")
                    val progress = (result.confirmedSpend.toFloat() / threshold.toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            CurrencyFormatter.formatCurrency(result.confirmedSpend, "MYR"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (result.thresholdMet) "10% tier active ✓"
                            else "${CurrencyFormatter.formatCurrency(result.remainingToThreshold, "MYR")} to 10% tier",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.thresholdMet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.uncertainSpend > BigDecimal.ZERO) {
                        Text(
                            "+ ${CurrencyFormatter.formatCurrency(result.uncertainSpend, "MYR")} near cycle close — may post next cycle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Per-category rebates ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    result.categories.forEach { cat ->
                        CategoryRow(
                            cat = cat,
                            thresholdMet = result.thresholdMet,
                            merchants = state.merchantsByCategory[cat.category].orEmpty(),
                            onMerchantClick = { reassignTarget = it }
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estimated rebate", style = MaterialTheme.typography.titleSmall)
                        Text(
                            CurrencyFormatter.formatCurrency(result.totalRebate, "MYR"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                "Captured transactions only (${state.capturedTxnCount}) — UOB tap-to-pay " +
                    "purchases send no SMS and must be added manually. Categories are " +
                    "inferred from merchant names; posting dates are estimated (+2 days). " +
                    "Tap a category to see its merchants and reassign any that are wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record a tap-to-pay purchase")
            }
        }
    }

    reassignTarget?.let { target ->
        ReassignCategoryDialog(
            target = target,
            onDismiss = { reassignTarget = null },
            onReassign = { category ->
                viewModel.reassign(target.merchant, category)
                reassignTarget = null
            },
            onClearOverride = {
                viewModel.clearOverride(target.merchant)
                reassignTarget = null
            }
        )
    }
}

private fun categoryLabel(category: UobRebateCategory): String = when (category) {
    UobRebateCategory.PETROL -> "Petrol"
    UobRebateCategory.GROCERIES -> "Groceries"
    UobRebateCategory.DINING -> "Dining"
    UobRebateCategory.GRAB -> "Grab"
    UobRebateCategory.OTHERS -> "Other spend"
}

@Composable
private fun CategoryRow(
    cat: UobCategoryResult,
    thresholdMet: Boolean,
    merchants: List<UobMerchantSpend>,
    onMerchantClick: (UobMerchantSpend) -> Unit,
) {
    val label = categoryLabel(cat.category)
    var expanded by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.then(
            if (merchants.isNotEmpty())
                Modifier.clickable { expanded = !expanded }
            else Modifier
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                CurrencyFormatter.formatCurrency(cat.rebate, "MYR"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        val detail = buildString {
            append("Spent ${CurrencyFormatter.formatCurrency(cat.spend, "MYR")}")
            when {
                cat.capped -> append(" · capped — further spend earns nothing here")
                cat.boostedSpendRemaining != null && thresholdMet ->
                    append(" · ${CurrencyFormatter.formatCurrency(cat.boostedSpendRemaining!!, "MYR")} more still earns 10%")
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (cat.capped) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded) {
            merchants.forEach { merchant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMerchantClick(merchant) }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        merchant.merchant + if (merchant.overridden) " 📌" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        CurrencyFormatter.formatCurrency(merchant.spend, "MYR"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReassignCategoryDialog(
    target: UobMerchantSpend,
    onDismiss: () -> Unit,
    onReassign: (UobRebateCategory) -> Unit,
    onClearOverride: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.merchant, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Pick the rebate category UOB actually credits this merchant under. " +
                        "The pin applies to every cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                UobRebateCategory.values().forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReassign(category) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = category == target.category,
                            onClick = { onReassign(category) }
                        )
                        Text(categoryLabel(category))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (target.overridden) {
                    TextButton(onClick = onClearOverride) { Text("Remove pin") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
