package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.compat.AndroidCompat
import com.example.data.local.NotificationItem
import com.example.data.local.ConversationRow
import com.example.data.local.SmsMessage
import com.example.data.sms.ContactItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemStatusState(
    val hasSmsPermissions: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val smsRepository = container.smsRepository
    private val contactsRepository = container.contactsRepository
    private val notificationRepository = container.notificationRepository

    val conversations: StateFlow<List<ConversationRow>> = smsRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = notificationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemStatusState())
    val systemStatus: StateFlow<SystemStatusState> = _systemStatus.asStateFlow()

    init {
        refreshSystemStatuses()
        syncAllIfPermitted()
    }

    fun refreshSystemStatuses() {
        val ctx = getApplication<Application>()
        _systemStatus.value = SystemStatusState(
            hasSmsPermissions = AndroidCompat.hasCoreSmsPermissions(ctx),
            hasContactsPermission = AndroidCompat.hasPermission(ctx, Manifest.permission.READ_CONTACTS),
            isDefaultSmsApp = AndroidCompat.isDefaultSmsApp(ctx),
            isNotificationListenerEnabled = AndroidCompat.isNotificationListenerEnabled(ctx)
        )
    }

    fun syncAllIfPermitted() {
        viewModelScope.launch {
            refreshSystemStatuses()
            _isSyncing.value = true
            try {
                if (_systemStatus.value.hasContactsPermission) {
                    _contacts.value = contactsRepository.loadContacts()
                }
                if (_systemStatus.value.hasSmsPermissions) {
                    smsRepository.syncFromSystemInbox()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun observeThreadMessages(threadId: Long): Flow<List<SmsMessage>> =
        smsRepository.observeThreadMessages(threadId)

    fun markThreadRead(threadId: Long) {
        viewModelScope.launch {
            smsRepository.markThreadRead(threadId)
        }
    }

    suspend fun resolveThreadForAddress(address: String): Long =
        smsRepository.resolveOrCreateThreadId(address)

    fun sendSms(threadId: Long?, address: String, body: String, onSentThread: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val result = smsRepository.sendSmsMessage(
                address = address,
                body = body,
                existingThreadId = threadId
            )
            result.onSuccess { resolvedThreadId ->
                onSentThread(resolvedThreadId)
            }
        }
    }

    fun deleteMessage(message: SmsMessage) {
        viewModelScope.launch {
            smsRepository.deleteMessage(message)
        }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            smsRepository.deleteThread(threadId)
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationRepository.deleteById(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationRepository.clearAll()
        }
    }

    fun buildSaveContactIntent(phoneNumber: String): Intent =
        contactsRepository.buildSaveOrEditContactIntent(phoneNumber)
}
