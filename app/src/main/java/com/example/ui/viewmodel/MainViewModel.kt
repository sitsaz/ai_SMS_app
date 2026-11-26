package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.compat.AndroidCompat
import com.example.data.local.ConversationRow
import com.example.data.local.NotificationItem
import com.example.data.local.SmsMessage
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import com.example.data.search.toSearchResult
import com.example.data.sms.ContactItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemStatusState(
    val hasSmsPermissions: Boolean = false,
    val hasReadSmsPermission: Boolean = false,
    val hasSendSmsPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val smsDao = container.database.smsDao()
    private val smsRepository = container.smsRepository
    private val notificationRepository = container.notificationRepository
    private val contactsRepository = container.contactsRepository
    private val settingsRepository = container.settingsRepository
    private val searchRepository = container.searchRepository

    private var bannerCheckedForSession = false
    private val _allowBannerThisSession = MutableStateFlow(false)
    private val _dismissedBannerThisSession = MutableStateFlow(false)

    val conversations: StateFlow<List<ConversationRow>> = smsRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = notificationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSmsSearchExpanded = MutableStateFlow(false)
    val isSmsSearchExpanded: StateFlow<Boolean> = _isSmsSearchExpanded.asStateFlow()

    private val _smsSearchQuery = MutableStateFlow("")
    val smsSearchQuery: StateFlow<String> = _smsSearchQuery.asStateFlow()

    val smsSearchResults: StateFlow<List<SearchResultItem>> = _smsSearchQuery
        .flatMapLatest { raw ->
            val cleanQuery = raw.trim()
            if (cleanQuery.isEmpty()) {
                flowOf(emptyList())
            } else {
                val ftsQuery = buildFtsPrefixQuery(cleanQuery)
                if (ftsQuery != null) {
                    combine(
                        smsDao.searchSmsFts(ftsQuery, 0L, 0L),
                        smsDao.searchSms(cleanQuery, 0L, 0L)
                    ) { ftsHits, likeHits ->
                        (ftsHits + likeHits)
                            .distinctBy { it.id }
                            .sortedByDescending { it.timestamp }
                            .map { it.toSearchResult() }
                    }
                } else {
                    smsDao.searchSms(cleanQuery, 0L, 0L)
                        .map { list -> list.map { it.toSearchResult() } }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemStatusState())
    val systemStatus: StateFlow<SystemStatusState> = _systemStatus.asStateFlow()

    val shouldShowDefaultSmsBanner: StateFlow<Boolean> = combine(
        _systemStatus,
        settingsRepository.settingsFlow,
        _allowBannerThisSession,
        _dismissedBannerThisSession
    ) { status, settings, allowSession, dismissed ->
        if (!status.hasSmsPermissions || status.isDefaultSmsApp || dismissed) {
            false
        } else if (allowSession) {
            true
        } else {
            val now = System.currentTimeMillis()
            val lastShown = settings.lastDefaultSmsBannerShownMs
            val dayElapsed = lastShown <= 0L || (now - lastShown) >= 86_400_000L
            if (dayElapsed && !bannerCheckedForSession) {
                bannerCheckedForSession = true
                _allowBannerThisSession.value = true
                viewModelScope.launch {
                    settingsRepository.setLastDefaultSmsBannerShownMs(now)
                }
                true
            } else {
                false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refreshSystemStatuses()
        syncAllIfPermitted()
        viewModelScope.launch {
            combine(_smsSearchQuery, smsSearchResults) { q, res -> q.trim() to res }
                .debounce(1_100L)
                .collectLatest { (cleanQuery, results) ->
                    if (cleanQuery.length >= 2) {
                        searchRepository.saveSearchToArchive(
                            query = cleanQuery,
                            origin = "SMS_BAR",
                            sourceFilter = SearchSourceFilter.SMS_ONLY,
                            results = results
                        )
                    }
                }
        }
    }

    fun saveCurrentSmsSearchToArchive() {
        val cleanQuery = _smsSearchQuery.value.trim()
        if (cleanQuery.isEmpty()) return
        val results = smsSearchResults.value
        viewModelScope.launch {
            searchRepository.saveSearchToArchive(
                query = cleanQuery,
                origin = "SMS_BAR",
                sourceFilter = SearchSourceFilter.SMS_ONLY,
                results = results
            )
        }
    }

    fun dismissDefaultSmsBannerToday() {
        _dismissedBannerThisSession.value = true
        _allowBannerThisSession.value = false
        viewModelScope.launch {
            settingsRepository.setLastDefaultSmsBannerShownMs(System.currentTimeMillis())
        }
    }

    fun setSmsSearchExpanded(expanded: Boolean) {
        _isSmsSearchExpanded.value = expanded
        if (!expanded) {
            _smsSearchQuery.value = ""
        }
    }

    fun updateSmsSearchQuery(query: String) {
        _smsSearchQuery.value = query
    }

    fun refreshSystemStatuses() {
        val ctx = getApplication<Application>()
        _systemStatus.value = SystemStatusState(
            hasSmsPermissions = AndroidCompat.hasCoreSmsPermissions(ctx),
            hasReadSmsPermission = AndroidCompat.hasReadSmsPermission(ctx),
            hasSendSmsPermission = AndroidCompat.hasSendSmsPermission(ctx),
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

    fun observePinnedMessagesInThread(threadId: Long): Flow<List<SmsMessage>> =
        smsRepository.observePinnedMessagesInThread(threadId)

    fun setMessagePinned(messageId: Long, pinned: Boolean) {
        viewModelScope.launch {
            smsRepository.setMessagePinned(messageId, pinned)
        }
    }

    fun setThreadPinned(threadId: Long, pinned: Boolean) {
        viewModelScope.launch {
            smsRepository.setThreadPinned(threadId, pinned)
        }
    }

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

    private fun buildFtsPrefixQuery(raw: String): String? {
        val tokens = raw.split(Regex("""\s+"""))
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
