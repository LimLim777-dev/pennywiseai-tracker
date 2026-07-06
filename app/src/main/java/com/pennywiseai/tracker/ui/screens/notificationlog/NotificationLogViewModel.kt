package com.pennywiseai.tracker.ui.screens.notificationlog

import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.data.database.dao.BankNotificationDao
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

@HiltViewModel
class NotificationLogViewModel @Inject constructor(
    dao: BankNotificationDao
) : ViewModel() {

    val notifications: StateFlow<List<BankNotificationEntity>> = dao.getAllNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
