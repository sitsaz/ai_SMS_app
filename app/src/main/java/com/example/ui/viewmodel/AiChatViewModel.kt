package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.localization.AppStrings
import com.example.core.localization.DateUtils
import com.example.data.ai.AiXmlResponseParser
import com.example.data.ai.ContextBudgetCalculator
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.archive.ConversationExportFormat
import com.example.data.archive.ConversationExportFormatter
import com.example.data.local.AiChatContextSource
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiContextMode
import com.example.data.local.AiTopicEntity
import com.example.data.local.ArchiveRangePreset
import com.example.data.local.ConversationRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationAppFilterOption(
    val packageName: String,
    val appName: String,
    val count: Int
)

data class AiChatContextConfig(
    val source: AiChatContextSource = AiChatContextSource.SELECTED_SMS_THREAD,
    val selectedThreadId: Long? = null,
    val selectedAppPackage: String? = null,
    val preset: ArchiveRangePreset = ArchiveRangePreset.ONE_WEEK,
    val customStartMs: Long = 0L,
    val customEndMs: Long = 0L,
    val maxItems: Int = 100,
    val smartContextEnabled: Boolean = true
)

data class AiChatArchiveSummary(
    val topic: AiTopicEntity,
    val messageCount: Int,
    val matchedRefsCount: Int,
    val firstUserPrompt: String,
    val latestAnswerPreview: String,
    val parsedSummary: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val smsDao = container.database.smsDao()
    private val notificationDao = container.database.notificationDao()
    private val chatArchiveRepo = container.chatArchiveRepository
    private val xmlBuilder = container.xmlArchiveBuilder
    private val aiClient = container.aiSearchClient
    private val settingsRepo = container.settingsRepository
    private val searchRepo = container.searchRepository

    val conversations: StateFlow<List<ConversationRow>> = smsDao.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notificationApps: StateFlow<List<NotificationAppFilterOption>> = notificationDao.observeAll()
        .map { items ->
            items.groupBy { it.packageName }
                .map { (pkg, group) ->
                    NotificationAppFilterOption(
                        packageName = pkg,
                        appName = group.firstOrNull()?.appName?.ifBlank { pkg } ?: pkg,
                        count = group.size
                    )
                }
                .sortedByDescending { it.count }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalNotificationCount: StateFlow<Int> = notificationDao.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _contextConfig = MutableStateFlow(AiChatContextConfig())
    val contextConfig: StateFlow<AiChatContextConfig> = _contextConfig.asStateFlow()

    private val _activeSessionTopicId = MutableStateFlow<Long?>(null)
    val activeSessionTopicId: StateFlow<Long?> = _activeSessionTopicId.asStateFlow()

    val messages: StateFlow<List<AiChatDisplayMessage>> = _activeSessionTopicId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                chatArchiveRepo.observeMessagesForTopic(id).map { list ->
                    list.map { entity ->
                        val parsed = AiXmlResponseParser.decodeFromJson(entity.structuredJson)
                        val refs = entity.matchedRefsCsv
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        AiChatDisplayMessage(
                            entity = entity,
                            matchedItems = searchRepo.hydrateRefs(refs),
                            parsedResponse = parsed
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedSessions: StateFlow<List<AiChatArchiveSummary>> = combine(
        chatArchiveRepo.observeTopics(),
        chatArchiveRepo.observeAllChatMessages()
    ) { topics, allMsgs ->
        val byTopic = allMsgs.groupBy { it.topicId }
        topics.map { topic ->
            val msgs = byTopic[topic.id].orEmpty()
            val firstUser = msgs.firstOrNull { it.role == "USER" }?.text.orEmpty()
            val lastAssistant = msgs.lastOrNull { it.role == "ASSISTANT" && !it.isError }
                ?: msgs.lastOrNull { it.role == "ASSISTANT" }
            val parsed = lastAssistant?.let { AiXmlResponseParser.decodeFromJson(it.structuredJson) }
            val refCount = msgs.flatMap { m ->
                m.matchedRefsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct().size
            AiChatArchiveSummary(
                topic = topic,
                messageCount = msgs.size,
                matchedRefsCount = refCount,
                firstUserPrompt = firstUser,
                latestAnswerPreview = lastAssistant?.text.orEmpty(),
                parsedSummary = parsed?.summary.orEmpty()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _xmlPreview = MutableStateFlow<XmlArchiveBuildResult?>(null)
    val xmlPreview: StateFlow<XmlArchiveBuildResult?> = _xmlPreview.asStateFlow()

    fun applyInitialRouteContext(sourceArg: String, threadIdArg: Long, topicIdArg: Long = -1L) {
        when {
            topicIdArg > 0L -> {
                loadArchivedSession(topicIdArg)
            }
            threadIdArg > 0L -> {
                _contextConfig.update {
                    it.copy(
                        source = AiChatContextSource.SELECTED_SMS_THREAD,
                        selectedThreadId = threadIdArg
                    )
                }
                _activeSessionTopicId.value = null
            }
            sourceArg.equals("NOTIFICATIONS", ignoreCase = true) -> {
                _contextConfig.update {
                    it.copy(source = AiChatContextSource.NOTIFICATION_HISTORY)
                }
                _activeSessionTopicId.value = null
            }
            else -> {
                val cur = _contextConfig.value
                if (cur.selectedThreadId == null && conversations.value.isNotEmpty()) {
                    _contextConfig.update {
                        it.copy(selectedThreadId = conversations.value.first().threadId)
                    }
                }
            }
        }
    }

    fun loadArchivedSession(topicId: Long) {
        viewModelScope.launch {
            val topic = chatArchiveRepo.getTopicById(topicId) ?: return@launch
            val restoredSource = runCatching { AiChatContextSource.valueOf(topic.contextSource) }
                .getOrElse {
                    when {
                        topic.selectedThreadId != null -> AiChatContextSource.SELECTED_SMS_THREAD
                        !topic.includeSms && topic.includeNotifications -> AiChatContextSource.NOTIFICATION_HISTORY
                        else -> AiChatContextSource.COMBINED_ARCHIVE
                    }
                }
            val restoredPreset = runCatching { ArchiveRangePreset.valueOf(topic.rangePreset) }
                .getOrDefault(ArchiveRangePreset.ONE_WEEK)
            val restoredSmart = topic.contextMode == AiContextMode.SMART_AUTO.name
            _contextConfig.value = AiChatContextConfig(
                source = restoredSource,
                selectedThreadId = topic.selectedThreadId ?: conversations.value.firstOrNull()?.threadId,
                selectedAppPackage = topic.selectedAppPackage,
                preset = restoredPreset,
                customStartMs = topic.customStartMs,
                customEndMs = topic.customEndMs,
                maxItems = topic.maxItems,
                smartContextEnabled = restoredSmart
            )
            _activeSessionTopicId.value = topicId
        }
    }

    fun deleteArchivedSession(topicId: Long) {
        viewModelScope.launch {
            chatArchiveRepo.deleteTopic(topicId)
            if (_activeSessionTopicId.value == topicId) {
                _activeSessionTopicId.value = null
            }
        }
    }

    fun selectSource(source: AiChatContextSource) {
        _contextConfig.update { cfg ->
            val fallbackThread = cfg.selectedThreadId ?: conversations.value.firstOrNull()?.threadId
            cfg.copy(source = source, selectedThreadId = fallbackThread)
        }
    }

    fun selectThread(threadId: Long) {
        _contextConfig.update {
            it.copy(
                source = AiChatContextSource.SELECTED_SMS_THREAD,
                selectedThreadId = threadId
            )
        }
    }

    fun selectNotificationApp(packageName: String?) {
        _contextConfig.update { it.copy(selectedAppPackage = packageName) }
    }

    fun selectRangePreset(preset: ArchiveRangePreset, startMs: Long = 0L, endMs: Long = 0L) {
        _contextConfig.update {
            it.copy(
                preset = preset,
                customStartMs = if (preset == ArchiveRangePreset.CUSTOM) startMs else it.customStartMs,
                customEndMs = if (preset == ArchiveRangePreset.CUSTOM) endMs else it.customEndMs
            )
        }
    }

    fun toggleSmartContext(enabled: Boolean) {
        _contextConfig.update { it.copy(smartContextEnabled = enabled) }
    }

    fun startNewSession() {
        _activeSessionTopicId.value = null
    }

    fun previewCurrentXmlPrompt(draftQuestion: String, isPersian: Boolean, strings: AppStrings) {
        viewModelScope.launch {
            val cfg = ensureEffectiveConfig()
            val settings = settingsRepo.getCurrentSettings()
            val selectedConv = conversations.value.firstOrNull { it.threadId == cfg.selectedThreadId }
            val threadLabel = selectedConv?.let {
                it.contactName?.takeIf { n -> n.isNotBlank() } ?: it.address
            }.orEmpty()
            val selectedAppLabel = notificationApps.value
                .firstOrNull { it.packageName == cfg.selectedAppPackage }?.appName

            val result = xmlBuilder.buildStructuredXmlPromptForSource(
                userQuestion = draftQuestion.ifBlank {
                    if (isPersian) "خلاصه و نکات مهم این تاریخچه چیست؟" else "Summarize key points from this history."
                },
                source = cfg.source,
                selectedThreadId = cfg.selectedThreadId,
                selectedThreadLabel = threadLabel,
                selectedAppPackage = cfg.selectedAppPackage,
                selectedAppLabel = selectedAppLabel,
                preset = cfg.preset,
                customStartMs = cfg.customStartMs,
                customEndMs = cfg.customEndMs,
                maxItems = cfg.maxItems,
                isPersian = isPersian,
                strings = strings,
                smartContextBudget = cfg.smartContextEnabled,
                contextLimitTokens = settings.aiContextLimitTokens,
                priorHistoryTokens = 0,
                preferFileUpload = cfg.smartContextEnabled && settings.aiSupportsFileUpload,
                maxOutputTokens = settings.aiMaxOutputTokens
            )
            _xmlPreview.value = XmlArchiveBuildResult(
                xmlContent = result.fileUploadXmlPayload ?: result.xmlPrompt,
                smsCount = result.smsCount,
                notificationCount = result.notificationCount,
                totalBytes = result.totalBytes,
                startMs = 0L,
                endMs = 0L,
                rangeLabel = result.sourceLabel,
                contextMode = if (cfg.smartContextEnabled) AiContextMode.SMART_AUTO else AiContextMode.INLINE_XML,
                estimatedTokens = result.estimatedTokens,
                contextLimitTokens = result.contextLimitTokens,
                totalAvailableInRange = result.totalCandidatesInRange,
                fileUploadPayload = result.fileUploadXmlPayload
            )
        }
    }

    fun showStoredXml(xmlContent: String, label: String) {
        _xmlPreview.value = XmlArchiveBuildResult(
            xmlContent = xmlContent,
            smsCount = 0,
            notificationCount = 0,
            totalBytes = xmlContent.toByteArray(Charsets.UTF_8).size,
            startMs = 0L,
            endMs = 0L,
            rangeLabel = label,
            contextMode = AiContextMode.INLINE_XML
        )
    }

    fun dismissXmlPreview() {
        _xmlPreview.value = null
    }

    suspend fun exportTopic(
        topicId: Long,
        format: ConversationExportFormat,
        isPersian: Boolean
    ): String? {
        val twm = chatArchiveRepo.observeTopicWithMessages(topicId).first() ?: return null
        return ConversationExportFormatter.formatAiChatTopic(twm, format, isPersian)
    }

    fun sendMessage(question: String, isPersian: Boolean, strings: AppStrings) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            try {
                val cfg = ensureEffectiveConfig()
                val settings = settingsRepo.getCurrentSettings()
                val selectedConv = conversations.value.firstOrNull { it.threadId == cfg.selectedThreadId }
                val threadLabel = selectedConv?.let {
                    it.contactName?.takeIf { n -> n.isNotBlank() } ?: it.address
                }.orEmpty()
                val selectedAppLabel = notificationApps.value
                    .firstOrNull { it.packageName == cfg.selectedAppPackage }?.appName

                val now = System.currentTimeMillis()
                var topicId = _activeSessionTopicId.value
                if (topicId == null) {
                    val titlePrefix = when (cfg.source) {
                        AiChatContextSource.SELECTED_SMS_THREAD -> threadLabel.ifBlank { strings.sourceSmsThread }
                        AiChatContextSource.NOTIFICATION_HISTORY -> selectedAppLabel ?: strings.sourceNotificationHistory
                        AiChatContextSource.COMBINED_ARCHIVE -> strings.sourceCombinedArchive
                    }
                    topicId = chatArchiveRepo.insertTopic(
                        AiTopicEntity(
                            title = "$titlePrefix: ${cleanQuestion.take(28)}",
                            createdAt = now,
                            updatedAt = now,
                            rangePreset = cfg.preset.name,
                            customStartMs = cfg.customStartMs,
                            customEndMs = cfg.customEndMs,
                            maxItems = cfg.maxItems,
                            includeSms = cfg.source != AiChatContextSource.NOTIFICATION_HISTORY,
                            includeNotifications = cfg.source != AiChatContextSource.SELECTED_SMS_THREAD,
                            contextMode = if (cfg.smartContextEnabled) AiContextMode.SMART_AUTO.name else AiContextMode.INLINE_XML.name,
                            contextSource = cfg.source.name,
                            selectedThreadId = cfg.selectedThreadId,
                            selectedAppPackage = cfg.selectedAppPackage,
                            originScreen = "AICHAT"
                        )
                    )
                    _activeSessionTopicId.value = topicId
                }

                val priorHistory = chatArchiveRepo.getMessagesForTopicOnce(topicId)
                val priorTokens = priorHistory.takeLast(6).sumOf {
                    ContextBudgetCalculator.estimateTokens(it.text)
                }

                val promptResult = xmlBuilder.buildStructuredXmlPromptForSource(
                    userQuestion = cleanQuestion,
                    source = cfg.source,
                    selectedThreadId = cfg.selectedThreadId,
                    selectedThreadLabel = threadLabel,
                    selectedAppPackage = cfg.selectedAppPackage,
                    selectedAppLabel = selectedAppLabel,
                    preset = cfg.preset,
                    customStartMs = cfg.customStartMs,
                    customEndMs = cfg.customEndMs,
                    maxItems = cfg.maxItems,
                    isPersian = isPersian,
                    strings = strings,
                    smartContextBudget = cfg.smartContextEnabled,
                    contextLimitTokens = settings.aiContextLimitTokens,
                    priorHistoryTokens = priorTokens,
                    preferFileUpload = cfg.smartContextEnabled && settings.aiSupportsFileUpload,
                    maxOutputTokens = settings.aiMaxOutputTokens
                )

                val kb = String.format(java.util.Locale.US, "%.1f KB", promptResult.totalBytes / 1024.0)
                val countLabel = DateUtils.formatNumber(promptResult.totalCount, isPersian)
                val totalAvailLabel = DateUtils.formatNumber(promptResult.totalCandidatesInRange, isPersian)
                val badgeLabel = if (cfg.smartContextEnabled) {
                    if (promptResult.fileUploadXmlPayload != null && settings.aiSupportsFileUpload) {
                        "${promptResult.sourceLabel} • $totalAvailLabel • $kb (${strings.smartFileUploadedBadge})"
                    } else {
                        val tokLabel = "${DateUtils.formatNumber(promptResult.estimatedTokens, isPersian)}/${DateUtils.formatNumber(promptResult.contextLimitTokens, isPersian)} tok"
                        "${promptResult.sourceLabel} • $countLabel/$totalAvailLabel • $tokLabel"
                    }
                } else {
                    "${promptResult.sourceLabel} • $countLabel • $kb XML"
                }

                chatArchiveRepo.insertChatMessage(
                    AiChatMessageEntity(
                        topicId = topicId,
                        role = "USER",
                        text = cleanQuestion,
                        xmlSummaryLabel = badgeLabel,
                        xmlContent = promptResult.fileUploadXmlPayload ?: promptResult.xmlPrompt,
                        timestamp = now
                    )
                )

                val aiResult = aiClient.askWithStructuredXmlPrompt(
                    settings = settings,
                    structuredXmlPrompt = promptResult.xmlPrompt,
                    priorHistory = priorHistory,
                    isPersian = isPersian,
                    smartAuto = cfg.smartContextEnabled,
                    fileUploadXmlPayload = promptResult.fileUploadXmlPayload,
                    userQuestionForFileMode = cleanQuestion
                )

                aiResult.onSuccess { parsed ->
                    chatArchiveRepo.insertChatMessage(
                        AiChatMessageEntity(
                            topicId = topicId,
                            role = "ASSISTANT",
                            text = parsed.answer,
                            matchedRefsCsv = parsed.matchedRefs.joinToString(","),
                            structuredJson = AiXmlResponseParser.encodeToJson(parsed),
                            timestamp = System.currentTimeMillis(),
                            isError = false
                        )
                    )
                }.onFailure { err ->
                    chatArchiveRepo.insertChatMessage(
                        AiChatMessageEntity(
                            topicId = topicId,
                            role = "ASSISTANT",
                            text = err.localizedMessage ?: err.message ?: "AI request failed",
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                    )
                }

                chatArchiveRepo.getTopicById(topicId)?.let { t ->
                    chatArchiveRepo.updateTopic(
                        t.copy(
                            updatedAt = System.currentTimeMillis(),
                            rangePreset = cfg.preset.name,
                            customStartMs = cfg.customStartMs,
                            customEndMs = cfg.customEndMs,
                            contextMode = if (cfg.smartContextEnabled) AiContextMode.SMART_AUTO.name else AiContextMode.INLINE_XML.name,
                            contextSource = cfg.source.name,
                            selectedThreadId = cfg.selectedThreadId,
                            selectedAppPackage = cfg.selectedAppPackage,
                            originScreen = "AICHAT"
                        )
                    )
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun ensureEffectiveConfig(): AiChatContextConfig {
        val current = _contextConfig.value
        if (current.source == AiChatContextSource.SELECTED_SMS_THREAD && current.selectedThreadId == null) {
            val firstTid = conversations.value.firstOrNull()?.threadId
            if (firstTid != null) {
                val updated = current.copy(selectedThreadId = firstTid)
                _contextConfig.value = updated
                return updated
            }
        }
        return current
    }
}
