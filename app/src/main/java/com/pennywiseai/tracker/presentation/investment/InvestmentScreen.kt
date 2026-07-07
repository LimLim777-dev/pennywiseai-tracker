package com.pennywiseai.tracker.presentation.investment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Investment tab v1 (plan T-I3). Account-level only: latest snapshot value,
 * invested capital from captured INVESTMENT transactions, unrealized P/L.
 * No live market data — values update when the user updates them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(
    onNavigateToManageAccounts: () -> Unit,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit,
    onRecordDividend: (bankName: String, accountLast4: String) -> Unit = { _, _ -> },
    viewModel: InvestmentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Investments") }) }
    ) { padding ->
        if (state.rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No investment accounts yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add each platform as an account with type Investment, " +
                        "then update its value whenever you check the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNavigateToManageAccounts) {
                    Text("Add investment account")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Extra bottom padding: the bottom navigation bar overlays content.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Portfolio value",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.formatByCurrency(state.totalValueByCurrency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Snapshot values — update each account after checking its app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(state.rows, key = { "${it.bankName}|${it.accountLast4}|${it.currency}" }) { row ->
                InvestmentAccountCard(
                    row = row,
                    onClick = { onAccountClick(row.bankName, row.accountLast4) },
                    onRecordDividend = { onRecordDividend(row.bankName, row.accountLast4) }
                )
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToManageAccounts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage accounts")
                }
            }
        }
    }
}

@Composable
private fun InvestmentAccountCard(
    row: InvestmentRow,
    onClick: () -> Unit,
    onRecordDividend: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.bankName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    CurrencyFormatter.formatCurrency(row.currentValue, row.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (row.invested != null && row.unrealizedPl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Invested ${CurrencyFormatter.formatCurrency(row.invested, row.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val gain = row.unrealizedPl.signum() >= 0
                    Text(
                        (if (gain) "+" else "−") +
                            CurrencyFormatter.formatCurrency(row.unrealizedPl.abs(), row.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gain) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    "No contributions captured yet — value only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap for value history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRecordDividend) {
                    Text("Record dividend", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
