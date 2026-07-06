package com.pennywiseai.tracker.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import com.pennywiseai.tracker.di.ApplicationScope
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TNGConfirmActivity : ComponentActivity() {

    @Inject lateinit var smsTransactionProcessor: SmsTransactionProcessor
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val amountStr = intent.getStringExtra(EXTRA_AMOUNT) ?: run { finish(); return }
        val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "TNG eWallet"
        val amount = amountStr.toBigDecimalOrNull() ?: run { finish(); return }
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: TNGTransactionKind.PAYMENT.name
        val kind = runCatching { TNGTransactionKind.valueOf(kindName) }.getOrDefault(TNGTransactionKind.PAYMENT)
        val txnTime = intent.getStringExtra(EXTRA_TIMESTAMP)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

        setContent {
            PennyWiseTheme {
                ConfirmDialog(
                    amount = amount,
                    initialMerchant = merchant,
                    kind = kind,
                    txnTime = txnTime,
                    onConfirm = { editedAmount, editedMerchant, description ->
                        saveTransaction(editedAmount, editedMerchant, description, txnTime, kind)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    @Composable
    private fun ConfirmDialog(
        amount: BigDecimal,
        initialMerchant: String,
        kind: TNGTransactionKind,
        txnTime: LocalDateTime?,
        onConfirm: (amount: BigDecimal, merchant: String, description: String?) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var merchant by remember { mutableStateOf(initialMerchant) }
        var description by remember { mutableStateOf("") }
        // OCR can misread the amount (e.g. large-font digits) — keep it editable.
        var amountText by remember { mutableStateOf(amount.toPlainString()) }
        val parsedAmount = amountText.trim().toBigDecimalOrNull()
        val amountValid = parsedAmount != null && parsedAmount > BigDecimal.ZERO
        val displayFmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        val typeLabel = when (kind) {
            TNGTransactionKind.RECEIVE -> "Income"
            else -> "Expense"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add TNG Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date / Time", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(txnTime?.format(displayFmt) ?: "Now", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Type", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(typeLabel, style = MaterialTheme.typography.bodyMedium)
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (kind == TNGTransactionKind.RECEIVE) "Amount (RM) +" else "Amount (RM) -") },
                        singleLine = true,
                        isError = !amountValid,
                        supportingText = if (!amountValid) {
                            { Text("Enter a valid amount") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = amountValid && merchant.isNotBlank(),
                    onClick = { onConfirm(parsedAmount!!, merchant.trim(), description.trim().ifBlank { null }) }
                ) {
                    Text("Add")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    private fun saveTransaction(
        amount: BigDecimal,
        merchant: String,
        description: String?,
        txnTime: LocalDateTime?,
        kind: TNGTransactionKind
    ) {
        val dateTime = txnTime ?: LocalDateTime.now()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("tng_${amount}_${dateTime}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

        val (type, category) = when (kind) {
            TNGTransactionKind.RECEIVE      -> TransactionType.INCOME to "Income"
            TNGTransactionKind.TRANSFER_OUT -> TransactionType.EXPENSE to "Transfer"
            TNGTransactionKind.PAYMENT      -> TransactionType.EXPENSE to "Others"
        }

        val entity = TransactionEntity(
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = dateTime,
            description = description,
            bankName = "TNG eWallet",
            smsSender = "TNG",
            currency = "MYR",
            transactionHash = hash,
        )
        // Shared pipeline path (review M-R1): cross-channel dedup + merchant
        // mapping + rules + subscription matching, atomically.
        appScope.launch {
            val result = smsTransactionProcessor.saveManualTransaction(entity)
            if (!result.success) {
                android.util.Log.i("TNGConfirmActivity", "Not saved: ${result.reason}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        applicationContext,
                        result.reason ?: "Not saved",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_AMOUNT    = "tng_amount"
        const val EXTRA_MERCHANT  = "tng_merchant"
        const val EXTRA_TIMESTAMP = "tng_timestamp"
        const val EXTRA_KIND      = "tng_kind"
    }
}
