package com.pennywiseai.tracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * The "we just recorded this" notification, shared by BOTH capture channels
 * (incoming SMS and bank-app notifications). Tapping it opens the
 * transaction for editing — merchant and description are exactly the fields
 * a parser can only guess at — and the action row offers one-tap
 * recategorisation without opening the app at all.
 *
 * Only shown while the app is in the background: if the user is already
 * looking at PennyWise, the row appears in the list on its own.
 */
object CapturedTransactionNotifier {

    private const val TAG = "CapturedTxnNotifier"
    const val CHANNEL_ID = "transaction_notifications"
    private const val CHANNEL_NAME = "Transaction Notifications"

    fun isAppInForeground(context: Context): Boolean = try {
        (context.applicationContext as? PennyWiseApplication)?.isAppInForeground ?: false
    } catch (e: Exception) {
        false
    }

    suspend fun show(
        context: Context,
        transactionId: Long,
        amount: BigDecimal,
        currency: String,
        merchant: String,
        type: String,
        bankName: String,
        category: String,
        repository: TransactionRepository,
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications for new transactions" }
            )

            val notificationId = transactionId.toInt()

            val editIntent = Intent(context, MainActivity::class.java).apply {
                action = SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION
                putExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, transactionId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val editPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val typeEmoji = when (type) {
                "EXPENSE" -> "💸"
                "INCOME" -> "💰"
                "CREDIT" -> "💳"
                "TRANSFER" -> "🔄"
                "INVESTMENT" -> "📈"
                else -> "💵"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("$typeEmoji ${CurrencyFormatter.formatCurrency(amount, currency)} — $merchant")
                .setContentText("$category • $bankName · tap to edit name/notes")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(editPendingIntent)
                .setAutoCancel(true)

            // Action slots are precious (Android collapses past 3): one for the
            // most-used other category, one for the full picker, one for Edit —
            // merchant/description can only be fixed in the app.
            val topCategories = try {
                repository.getTopCategoriesByUsage(3)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching top categories", e)
                listOf("Food & Dining", "Shopping", "Transportation")
            }
            topCategories.filter { it != category }.take(1).forEachIndexed { index, topCategory ->
                val categoryIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_CHANGE_CATEGORY
                    putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(NotificationActionReceiver.EXTRA_NEW_CATEGORY, topCategory)
                }
                builder.addAction(
                    0,
                    topCategory,
                    PendingIntent.getBroadcast(
                        context,
                        notificationId + index + 1,
                        categoryIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            val pickerIntent = Intent(context, QuickCategoryPickerActivity::class.java).apply {
                putExtra(QuickCategoryPickerActivity.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(QuickCategoryPickerActivity.EXTRA_NOTIFICATION_ID, notificationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            builder.addAction(
                0,
                "Category…",
                PendingIntent.getActivity(
                    context,
                    notificationId + 100,
                    pickerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            builder.addAction(0, "Edit", editPendingIntent)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
}
