package com.pennywiseai.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_ADD_TRANSACTION = "com.pennywiseai.tracker.OPEN_ADD_TRANSACTION"
    }

    // Transaction ID to edit when launched from notification
    var editTransactionId by mutableStateOf<Long?>(null)
        private set

    // Flag to navigate directly to Add Transaction when launched from a shortcut/widget
    var openAddTransaction by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent if activity is launched from notification
        handleEditIntent(intent)

        val editCompleteCallback = { editTransactionId = null }
        val addShortcutCallback = { openAddTransaction = false }

        setContent {
            PennyWiseApp(
                editTransactionId = editTransactionId,
                openAddTransaction = openAddTransaction,
                onEditComplete = editCompleteCallback,
                onAddTransactionShortcutHandled = addShortcutCallback
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle intent when activity is already running
        handleEditIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Android often leaves the notification listener unbound after an APK
        // update (and OEM battery managers wedge it in daily use) — every
        // bank-app notification is silently dropped meanwhile. If access is
        // granted but the listener is dead, force re-registration by toggling
        // the component and requesting a rebind (no-op when healthy).
        com.pennywiseai.tracker.receiver.BankNotificationListenerService
            .ensureListenerAlive(this)
        requestBatteryOptimizationExemptionOnce()
    }

    /**
     * OEM battery "optimization" is the main reason the notification listener
     * keeps dying between app launches. Ask the system (once) to exempt the
     * app; the user sees the standard Android allow/deny dialog. Never asked
     * again after a deny — the Notification Log banner remains the manual path.
     */
    private fun requestBatteryOptimizationExemptionOnce() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("battery_exemption_asked", true).apply()
        try {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            // Some OEMs block the direct request — the user can still exempt
            // the app manually from system settings.
        }
    }

    private fun handleEditIntent(intent: Intent?) {
        if (intent?.action == SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION) {
            val transactionId = intent.getLongExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, -1)
            if (transactionId != -1L) {
                editTransactionId = transactionId
            }
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true) {
            openAddTransaction = true
        }
    }
}
