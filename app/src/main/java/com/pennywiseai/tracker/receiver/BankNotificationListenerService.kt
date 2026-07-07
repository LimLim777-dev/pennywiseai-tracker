package com.pennywiseai.tracker.receiver

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pennywiseai.tracker.data.repository.BankNotificationRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import com.pennywiseai.tracker.data.database.entity.TransactionType as EntityTransactionType
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.TransactionType as ParsedTransactionType
import com.pennywiseai.tracker.worker.BankNotificationRetryWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notification listener that ingests bank app notifications and routes them
 * through the existing parser pipeline.
 */
class BankNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        fun smsTransactionProcessor(): SmsTransactionProcessor
        fun bankNotificationRepository(): BankNotificationRepository
        fun transactionRepository(): TransactionRepository
    }

    companion object {
        private const val TAG = "BankNotificationListener"

        /**
         * True while the system has this listener bound. Android routinely
         * fails to rebind notification listeners after an APK update (until
         * a reboot or an access toggle) — observed 2026-07-07 as a silent
         * multi-day capture gap. Surfaced on the Notification Log screen so
         * the gap is visible instead of silent.
         */
        @Volatile
        var isConnected: Boolean = false
            private set

        /** Whether the user has granted notification access to this app. */
        fun hasNotificationAccess(context: android.content.Context): Boolean =
            androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)

        /**
         * Nudge the system to rebind the listener. Safe to call on every app
         * foreground: no-op when access isn't granted, and rebinding an
         * already-bound listener is harmless. This is the canonical
         * workaround for the post-update unbind bug.
         */
        fun requestRebindIfPermitted(context: android.content.Context) {
            if (!hasNotificationAccess(context)) return
            try {
                requestRebind(
                    android.content.ComponentName(context, BankNotificationListenerService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind failed", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        if (!BankNotificationConfig.isAllowed(packageName)) {
            return
        }

        // Skip group summaries to avoid duplicate processing
        if ((sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val body = BankNotificationConfig.extractMessage(sbn.notification)
        if (body.isBlank()) {
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java
        )
        val processor = entryPoint.smsTransactionProcessor()
        val notificationRepository = entryPoint.bankNotificationRepository()
        val transactionRepository = entryPoint.transactionRepository()
        val senderAlias = BankNotificationConfig.senderAlias(packageName)
        val timestamp = sbn.postTime

        serviceScope.launch {
            var notificationId: Long? = null
            try {
                notificationId = notificationRepository.logNotification(
                    packageName = packageName,
                    senderAlias = senderAlias,
                    messageBody = body,
                    postedAtMillis = timestamp
                ).takeIf { it > 0 }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store bank notification", e)
            }

            try {
                // Cross-dedup: check if SMS already created this transaction.
                // Parse first to get amount, then look for an existing transaction
                // with the same amount within a ±2-minute window.
                // Content-aware dispatch (like the processor): single-parser
                // resolution can be shadowed by sender-ID overlaps (e.g. T-Bank
                // vs BoostBank), which silently skipped this whole pre-pass.
                val parsed = BankParserFactory.getParsers(senderAlias)
                    .firstNotNullOfOrNull { it.parse(body, senderAlias, timestamp) }
                if (parsed != null) {
                    val eventTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()
                    )
                    val windowStart = eventTime.minusMinutes(2)
                    val windowEnd = eventTime.plusMinutes(2)
                    val existing = transactionRepository.getTransactionByAmountAndDate(
                        parsed.amount, windowStart, windowEnd
                    )
                    if (existing.any { it.bankName == parsed.bankName }) {
                        Log.d(TAG, "Notification skipped: duplicate of SMS transaction")
                        if (notificationId != null) {
                            notificationRepository.markProcessed(notificationId, null)
                        }
                        return@launch
                    }

                    // Cross-bank self-transfer: if Boost Bank reports income but another
                    // bank already logged a TRANSFER of the same amount in the same window,
                    // reclassify this as TRANSFER (Boost income notifications omit sender name)
                    if (parsed.type == ParsedTransactionType.INCOME && parsed.bankName == "Boost Bank") {
                        val hasMatchingTransfer = existing.any {
                            it.transactionType == EntityTransactionType.TRANSFER && it.bankName != parsed.bankName
                        }
                        if (hasMatchingTransfer) {
                            Log.d(TAG, "Boost Bank income reclassified as cross-bank TRANSFER")
                            val corrected = parsed.copy(type = ParsedTransactionType.TRANSFER)
                            val result = processor.saveParsedTransaction(corrected, body)
                            if (notificationId != null) {
                                notificationRepository.markProcessed(notificationId, result.transactionId)
                            }
                            return@launch
                        }
                    }
                }

                val result = processor.processAndSaveTransaction(
                    sender = senderAlias,
                    body = body,
                    timestamp = timestamp
                )
                if (!result.success) {
                    Log.d(TAG, "Notification skipped: ${result.reason}")
                } else if (notificationId != null) {
                    notificationRepository.markProcessed(notificationId, result.transactionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process bank notification", e)
                BankNotificationRetryWorker.enqueue(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
