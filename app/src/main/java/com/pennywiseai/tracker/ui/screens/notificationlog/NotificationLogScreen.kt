package com.pennywiseai.tracker.ui.screens.notificationlog

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import com.pennywiseai.tracker.receiver.BankNotificationListenerService
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationLogViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    // Sender filter — the log is the sample-collection tool for parser work;
    // filtering to one app beats scrolling the merged stream.
    var senderFilter by remember { mutableStateOf<String?>(null) }
    val senders = remember(notifications) {
        notifications.map { it.senderAlias }.distinct().sorted()
    }
    val visibleNotifications = remember(notifications, senderFilter) {
        if (senderFilter == null) notifications
        else notifications.filter { it.senderAlias == senderFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListenerStatusBanner()
            if (senders.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = senderFilter == null,
                            onClick = { senderFilter = null },
                            label = { Text("All") }
                        )
                    }
                    items(senders) { sender ->
                        FilterChip(
                            selected = senderFilter == sender,
                            onClick = {
                                senderFilter = if (senderFilter == sender) null else sender
                            },
                            label = { Text(sender) }
                        )
                    }
                }
            }
            if (visibleNotifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (notifications.isEmpty()) "No notifications received yet"
                               else "No notifications from ${senderFilter ?: "this sender"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(visibleNotifications.reversed()) { _, item ->
                        NotificationLogCard(item)
                    }
                }
            }
        }
    }
}

/**
 * Surfaces the listener's live bind state. Android routinely leaves
 * notification listeners unbound after an APK update — every bank
 * notification is silently dropped until a rebind. Green = capturing;
 * red = fix it now (tap opens the system Notification access screen).
 */
@Composable
private fun ListenerStatusBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(BankNotificationListenerService.hasNotificationAccess(context)) }
    var connected by remember { mutableStateOf(BankNotificationListenerService.isConnected) }
    // Nudge a rebind and keep polling while this screen is visible, so the
    // banner flips green once the rebind lands (and re-checks after the user
    // returns from the system settings toggle).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BankNotificationListenerService.requestRebindIfPermitted(context)
            while (true) {
                hasAccess = BankNotificationListenerService.hasNotificationAccess(context)
                connected = BankNotificationListenerService.isConnected
                delay(1_000)
            }
        }
    }
    val (text, isError) = when {
        !hasAccess -> "Notification access NOT granted — bank app notifications are not captured. Tap to open settings." to true
        !connected -> "Listener not connected (common after app updates) — reconnecting… If this stays red, toggle Notification access off/on in settings or reboot. Tap to open settings." to true
        else -> "Listener connected — capturing bank notifications." to false
    }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (isError) Modifier.clickable { openSettings() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun NotificationLogCard(item: BankNotificationEntity) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM · h:mm a") }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.processed)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.senderAlias,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.postedAt.format(formatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Verbatim message bodies are the raw material for every parser
                    // task — one tap beats retyping from a screenshot.
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(item.messageBody))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy message body",
                            modifier = Modifier.size(16.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = item.messageBody,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.processed) "✓ Processed${item.transactionId?.let { " (tx #$it)" } ?: ""}"
                       else "✗ Not processed",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.processed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
        }
    }
}
