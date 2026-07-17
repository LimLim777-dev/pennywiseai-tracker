package com.pennywiseai.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.receiver.BankNotificationListenerService
import java.util.concurrent.TimeUnit

/**
 * Periodic guard for the notification-capture channel. Android (OEM battery
 * managers especially) revokes notification access or wedges the listener
 * without telling the user — every bank notification is then silently lost
 * (2026-07-07 incident: 3 days of missed captures).
 *
 * Two failure modes, two responses:
 * - access GRANTED but listener dead → self-heal in place
 *   ([BankNotificationListenerService.ensureListenerAlive]);
 * - access REVOKED → the app cannot re-grant it, so post a high-priority
 *   system notification that deep-links to the Notification access screen.
 */
class ListenerHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        if (BankNotificationListenerService.hasNotificationAccess(context)) {
            BankNotificationListenerService.ensureListenerAlive(context)
            // Access is fine — retire any stale revoked-access alert.
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } else {
            postAccessRevokedNotification(context)
        }
        return Result.success()
    }

    private fun postAccessRevokedNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Capture health",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when bank-notification capture stops working"
            }
        )
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Notification access is OFF")
            .setContentText("Bank notifications are NOT being captured. Tap to re-enable PennyWise.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The system disabled PennyWise's Notification access — bank app " +
                        "notifications are NOT being captured. Tap to open settings and " +
                        "switch it back on."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val WORK_NAME = "listener_health_check"
        private const val CHANNEL_ID = "listener_health"
        private const val NOTIFICATION_ID = 4207

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListenerHealthWorker>(4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
