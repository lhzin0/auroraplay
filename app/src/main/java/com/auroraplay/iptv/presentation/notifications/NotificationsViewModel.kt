package com.auroraplay.iptv.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.data.datastore.AppNotification
import com.auroraplay.iptv.data.datastore.NotificationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationStore: NotificationStore,
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = notificationStore.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAllRead() = viewModelScope.launch { notificationStore.markAllRead() }
}
