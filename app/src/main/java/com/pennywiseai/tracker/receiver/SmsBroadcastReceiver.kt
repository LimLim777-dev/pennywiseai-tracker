package com.pennywiseai.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts incoming SMS messages in real-time
 * and processes them for transaction data using the shared SmsTransactionProcessor.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsBroadcastReceiverEntryPoint {
        fun smsTransactionProcessor(): SmsTransactionProcessor
        fun transactionRepository(): com.pennywiseai.tracker.data.repository.TransactionRepository
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        const val ACTION_EDIT_TRANSACTION = "com.pennywiseai.tracker.ACTION_EDIT_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val CHANNEL_ID = "transaction_notifications"
        const val CHANNEL_NAME = "Transaction Notifications"
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Combine multi-part SMS messages with their timestamps
        data class SmsData(val body: StringBuilder, var timestamp: Long)
        val smsMap = mutableMapOf<String, SmsData>()
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val timestamp = message.timestampMillis

            val existing = smsMap.getOrPut(sender) { SmsData(StringBuilder(), timestamp) }
            existing.body.append(body)
            // Use the earliest timestamp for multi-part messages
            if (timestamp < existing.timestamp) {
                existing.timestamp = timestamp
            }
        }

        // Get the processor via Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsBroadcastReceiverEntryPoint::class.java
        )
        val processor = entryPoint.smsTransactionProcessor()

        // Process each unique SMS
        for ((sender, smsData) in smsMap) {
            val body = smsData.body.toString()
            val timestamp = smsData.timestamp
            Log.d(TAG, "Received SMS from: $sender at timestamp: $timestamp")

            processIncomingSms(context, processor, sender, body, timestamp)
        }
    }

    private fun processIncomingSms(
        context: Context,
        processor: SmsTransactionProcessor,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        receiverScope.launch {
            try {
                // Use the shared processor to parse and save the transaction
                val result = processor.processAndSaveTransaction(sender, body, timestamp)

                if (result.success && result.transactionId != null) {
                    Log.d(TAG, "Transaction saved with ID: ${result.transactionId}")

                    // Show notification if app is not in foreground
                    if (!isAppInForeground(context)) {
                        // Get transaction details for notification
                        // Content-aware dispatch (same as processAndSaveTransaction) so
                        // shared-sender banks (M-Pesa KE/TZ/MZ) re-parse with the right parser.
                        val parsedTransaction = com.pennywiseai.parser.core.bank.BankParserFactory
                            .parse(body, sender, timestamp)

                        if (parsedTransaction != null) {
                            // Get entry point to access repository
                            val entryPoint = EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                SmsBroadcastReceiverEntryPoint::class.java
                            )
                            val repository = entryPoint.transactionRepository()

                            // Fetch the saved transaction to get its category
                            val savedTransaction = repository.getTransactionById(result.transactionId)

                            CapturedTransactionNotifier.show(
                                context = context,
                                transactionId = result.transactionId,
                                amount = parsedTransaction.amount,
                                currency = savedTransaction?.currency
                                    ?: parsedTransaction.currency ?: "MYR",
                                merchant = parsedTransaction.merchant ?: "Unknown",
                                type = parsedTransaction.type.name,
                                bankName = parsedTransaction.bankName ?: "Bank",
                                category = savedTransaction?.category ?: "Others",
                                repository = repository
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "Transaction not saved: ${result.reason}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }
    }

    private fun isAppInForeground(context: Context): Boolean =
        CapturedTransactionNotifier.isAppInForeground(context)

}
