package com.pennywiseai.tracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import com.pennywiseai.tracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenshotObserver(
    private val context: Context,
    private val scope: CoroutineScope,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "ScreenshotObserver"
        const val CHANNEL_ID = "shopeepay_screenshot"
        private const val NOTIF_ID = 9001
        private const val NOTIF_ID_TNG = 9002
    }

    init {
        createNotificationChannel()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (!hasMediaPermission()) return
        scope.launch(Dispatchers.IO) {
            val imageUri = findLatestScreenshot(limitToRecentSeconds = 10) ?: return@launch

            val shopeeResult = ShopeePayScreenshotParser.parse(context, imageUri)
            if (shopeeResult != null) {
                Log.d(TAG, "ShopeePay screenshot detected: RM${shopeeResult.amount} @ ${shopeeResult.merchant}")
                postConfirmNotification(imageUri, shopeeResult)
                return@launch
            }

            val tngResult = TNGScreenshotParser.parse(context, imageUri)
            if (tngResult != null) {
                Log.d(TAG, "TNG screenshot detected: RM${tngResult.amount} @ ${tngResult.merchant}")
                postTNGConfirmNotification(imageUri, tngResult)
            }
        }
    }

    fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Manually process the most recent screenshot (no time filter) — used by Settings test button.
    suspend fun processLatestManually(): Boolean {
        val imageUri = findLatestScreenshot(limitToRecentSeconds = null) ?: return false
        return processUri(imageUri)
    }

    // Process a specific image URI (e.g. picked from gallery).
    suspend fun processUri(uri: Uri): Boolean {
        val shopeeResult = ShopeePayScreenshotParser.parse(context, uri)
        if (shopeeResult != null) {
            postConfirmNotification(uri, shopeeResult)
            return true
        }
        val tngResult = TNGScreenshotParser.parse(context, uri)
        if (tngResult != null) {
            postTNGConfirmNotification(uri, tngResult)
            return true
        }
        return false
    }

    // Query MediaStore for a screenshot saved in the last N seconds (null = no time limit).
    private fun findLatestScreenshot(limitToRecentSeconds: Int? = 10): Uri? {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
            )
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (limitToRecentSeconds != null) {
                val cutoff = (System.currentTimeMillis() / 1000) - limitToRecentSeconds
                conditions.add("${MediaStore.Images.Media.DATE_ADDED} >= ?")
                args.add(cutoff.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                conditions.add(
                    "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
                )
                args.add("%Screenshot%")
                args.add("Screenshot%")
            }
            val selection = conditions.joinToString(" AND ").ifEmpty { null }
            val selectionArgs = args.toTypedArray().ifEmpty { null }

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                val name = cursor.getString(nameCol) ?: ""

                // On older Android without RELATIVE_PATH, check the display name
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    !name.contains("Screenshot", ignoreCase = true)) return null

                val id = cursor.getLong(idCol)
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query latest screenshot", e)
            null
        }
    }

    private fun postConfirmNotification(imageUri: Uri, result: ShopeePayParseResult) {
        val intent = Intent(context, ShopeePayConfirmActivity::class.java).apply {
            putExtra(ShopeePayConfirmActivity.EXTRA_AMOUNT, result.amount.toPlainString())
            putExtra(ShopeePayConfirmActivity.EXTRA_MERCHANT, result.merchant)
            putExtra(ShopeePayConfirmActivity.EXTRA_IMAGE_URI, imageUri.toString())
            putExtra(ShopeePayConfirmActivity.EXTRA_IS_TRANSFER, result.isTransfer)
            result.timestamp?.let { putExtra(ShopeePayConfirmActivity.EXTRA_TIMESTAMP, it.toString()) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ShopeePay — RM${result.amount}")
            .setContentText("Tap to add: ${result.merchant}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        }
    }

    private fun postTNGConfirmNotification(imageUri: Uri, result: TNGParseResult) {
        val intent = Intent(context, TNGConfirmActivity::class.java).apply {
            putExtra(TNGConfirmActivity.EXTRA_AMOUNT, result.amount.toPlainString())
            putExtra(TNGConfirmActivity.EXTRA_MERCHANT, result.merchant)
            putExtra(TNGConfirmActivity.EXTRA_KIND, result.kind.name)
            result.timestamp?.let { putExtra(TNGConfirmActivity.EXTRA_TIMESTAMP, it.toString()) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, NOTIF_ID_TNG, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (result.kind) {
            TNGTransactionKind.RECEIVE -> "TNG — +RM${result.amount}"
            else -> "TNG — -RM${result.amount}"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Tap to add: ${result.merchant}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TNG, notif)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ShopeePay Screenshot",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prompts you to log ShopeePay QR payments detected from screenshots"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
