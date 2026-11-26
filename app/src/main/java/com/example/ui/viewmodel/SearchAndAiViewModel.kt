package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.localization.AppStrings
import com.example.core.localization.DateUtils
import com.example.data.ai.AiParsedSearchResponse
import com.example.data.ai.AiXmlResponseParser
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiContextMode
import com.example.data.local.AiTopicEntity
import com.example.data.local.ArchiveRangePreset
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchSortOrder
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalSearchFilterState(
    val query: String = "",
    val sortOrder: SearchSortOrder = SearchSortOrder.NEWEST_FIRST,
    val sourceFilter: SearchSourceFilter = SearchSourceFilter.ALL,
    val directionFilter: SearchDirectionFilter = SearchDirectionFilter.ALL,
    val startMs: Long = 0L,
    val endMs: Long = 0L
)

data class AiScopeDraft(
    val preset: ArchiveRangePreset = ArchiveRangePreset.ONE_WEEK,
    val customStartMs: Long = 0L,
    val customEndMs: Long = 0L,
    val maxItems: Int = 150,
    val includeSms: Boolean = true,
    val includeNotifications: Boolean = true,
    val contextMode: AiContextMode = AiContextMode.COMPACT_TEXT
)

data class AiChatDisplayMessage(
    val entity: AiChatMessageEntity,
    val matchedItems: List<SearchResultItem>,
    val parsedResponse: AiParsedSearchResponse? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchAndAiViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val searchRepo = container.searchRepository
    private val aiTopicDao = container.database.aiTopicDao()
    private val xmlBuilder = container.xmlArchiveBuilder
    private val aiClient = container.aiSearchClient
    private val settingsRepo = container.settingsRepository

    // Local Search State
    private val _filterState = MutableStateFlow(LocalSearchFilterState())
    val filterState: StateFlow<LocalSearchFilterState> = _filterState.asStateFlow()

    val localResults: StateFlow<List<SearchResultItem>> = _filterState
        .flatMapLatest { f ->
            searchRepo.searchUnified(
                query = f.query,
                sourceFilter = f.sourceFilter,
                directionFilter = f.directionFilter,
                sortOrder = f.sortOrder,
                startMs = f.startMs,
                endMs = f.endMs
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // AI Topics & Chat State
    val topics: StateFlow<List<AiTopicEntity>> = aiTopicDao.observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeTopicId = MutableStateFlow<Long?>(null)
    val activeTopicId: StateFlow<Long?> = _activeTopicId.asStateFlow()

    val activeTopic: StateFlow<AiTopicEntity?> = _activeTopicId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else aiTopicDao.observeTopic(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chatMessages: StateFlow<List<AiChatDisplayMessage>> = _activeTopicId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                aiTopicDao.observeMessagesForTopic(id).map { list ->
                    list.map { entity ->
                        val parsed = AiXmlResponseParser.decodeFromJson(entity.structuredJson)
                        val refs = entity.matchedRefsCsv
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val hydrated = searchRepo.hydrateRefs(refs)
                        AiChatDisplayMessage(
                            entity = entity,
                            matchedItems = hydrated,
                            parsedResponse = parsed
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAiSending = MutableStateFlow(false)
    val isAiSending: StateFlow<Boolean> = _isAiSending.asStateFlow()

    // Pre-send archive time range sheet state
    private val _showScopeSheet = MutableStateFlow(false)
    val showScopeSheet: StateFlow<Boolean> = _showScopeSheet.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<String?>(null)
    val pendingQuestion: StateFlow<String?> = _pendingQuestion.asStateFlow()

    private val _scopeDraft = MutableStateFlow(AiScopeDraft())
    val scopeDraft: StateFlow<AiScopeDraft> = _scopeDraft.asStateFlow()

    private val _draftCounts = MutableStateFlow(0 to 0)
    val draftCounts: StateFlow<Pair<Int, Int>> = _draftCounts.asStateFlow()

    private val _xmlPreview = MutableStateFlow<XmlArchiveBuildResult?>(null)
    val xmlPreview: StateFlow<XmlArchiveBuildResult?> = _xmlPreview.asStateFlow()

    init {
        refreshDraftCounts()
    }

    // Local Search Mutators
    fun updateQuery(query: String) {
        _filterState.value = _filterState.value.copy(query = query)
    }

    fun updateSortOrder(sort: SearchSortOrder) {
        _filterState.value = _filterState.value.copy(sortOrder = sort)
    }

    fun updateSourceFilter(source: SearchSourceFilter) {
        _filterState.value = _filterState.value.copy(sourceFilter = source)
    }

    fun updateDirectionFilter(direction: SearchDirectionFilter) {
        _filterState.value = _filterState.value.copy(directionFilter = direction)
    }

    fun updateLocalDateRange(startMs: Long, endMs: Long) {
        _filterState.value = _filterState.value.copy(startMs = startMs, endMs = endMs)
    }

    fun clearLocalDateRange() {
        _filterState.value = _filterState.value.copy(startMs = 0L, endMs = 0L)
    }

    // AI Topic Mutators
    fun selectTopic(topicId: Long?) {
        _activeTopicId.value = topicId
        if (topicId != null) {
            viewModelScope.launch {
                val topic = aiTopicDao.getTopicById(topicId)
                if (topic != null) {
                    val preset = runCatching { ArchiveRangePreset.valueOf(topic.rangePreset) }
                        .getOrDefault(ArchiveRangePreset.ONE_WEEK)
                    val ctxMode = runCatching { AiContextMode.valueOf(topic.contextMode) }
                        .getOrDefault(AiContextMode.COMPACT_TEXT)
                    _scopeDraft.value = AiScopeDraft(
                        preset = preset,
                        customStartMs = topic.customStartMs,
                        customEndMs = topic.customEndMs,
                        maxItems = topic.maxItems,
                        includeSms = topic.includeSms,
                        includeNotifications = topic.includeNotifications,
                        contextMode = ctxMode
                    )
                    refreshDraftCounts()
                }
            }
        }
    }

    fun startNewTopic() {
        _activeTopicId.value = null
        _scopeDraft.value = AiScopeDraft()
        refreshDraftCounts()
    }

    fun deleteTopic(topicId: Long) {
        viewModelScope.launch {
            aiTopicDao.deleteTopic(topicId)
            if (_activeTopicId.value == topicId) {
                _activeTopicId.value = null
            }
        }
    }

    // Pre-send Scope Sheet triggers
    fun requestScopeAndSend(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _isAiSending.value) return
        _pendingQuestion.value = trimmed
        refreshDraftCounts()
        _showScopeSheet.value = true
    }

    fun openScopeEditorOnly() {
        _pendingQuestion.value = null
        refreshDraftCounts()
        _showScopeSheet.value = true
    }

    fun dismissScopeSheet() {
        _showScopeSheet.value = false
        _pendingQuestion.value = null
    }

    fun updateScopeDraft(transform: (AiScopeDraft) -> AiScopeDraft) {
        _scopeDraft.value = transform(_scopeDraft.value)
        refreshDraftCounts()
    }

    fun refreshDraftCounts() {
        viewModelScope.launch {
            val d = _scopeDraft.value
            _draftCounts.value = xmlBuilder.countInWindow(
                preset = d.preset,
                customStartMs = d.customStartMs,
                customEndMs = d.customEndMs,
                includeSms = d.includeSms,
                includeNotifications = d.includeNotifications
            )
        }
    }

    fun generateXmlPreview(isPersian: Boolean, strings: AppStrings) {
        viewModelScope.launch {
            val d = _scopeDraft.value
            val queryHint = _pendingQuestion.value.orEmpty()
            _xmlPreview.value = xmlBuilder.buildContextForAi(
                question = queryHint,
                contextMode = d.contextMode,
                preset = d.preset,
                customStartMs = d.customStartMs,
                customEndMs = d.customEndMs,
                maxItems = d.maxItems,
                includeSms = d.includeSms,
                includeNotifications = d.includeNotifications,
                isPersian = isPersian,
                strings = strings
            )
        }
    }

    fun showExistingXml(xmlContent: String, rangeLabel: String, totalBytes: Int) {
        _xmlPreview.value = XmlArchiveBuildResult(
            xmlContent = xmlContent,
            smsCount = 0,
            notificationCount = 0,
            totalBytes = totalBytes,
            startMs = 0L,
            endMs = 0L,
            rangeLabel = rangeLabel
        )
    }

    fun dismissXmlPreview() {
        _xmlPreview.value = null
    }

    fun confirmScopeAndSendIfPending(isPersian: Boolean, strings: AppStrings) {
        val question = _pendingQuestion.value
        val draft = _scopeDraft.value
        _showScopeSheet.value = false
        _pendingQuestion.value = null

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var topicId = _activeTopicId.value

            if (topicId == null) {
                val defaultTitle = question?.take(42)?.let {
                    if (question.length > 42) "$it…" else it
                } ?: if (isPersian) "جستجوی جدید" else "New AI Topic"
                topicId = aiTopicDao.insertTopic(
                    AiTopicEntity(
                        title = defaultTitle,
                        createdAt = now,
                        updatedAt = now,
                        rangePreset = draft.preset.name,
                        customStartMs = draft.customStartMs,
                        customEndMs = draft.customEndMs,
                        maxItems = draft.maxItems,
                        includeSms = draft.includeSms,
                        includeNotifications = draft.includeNotifications,
                        contextMode = draft.contextMode.name
                    )
                )
                _activeTopicId.value = topicId
            } else {
                val existing = aiTopicDao.getTopicById(topicId)
                if (existing != null) {
                    aiTopicDao.updateTopic(
                        existing.copy(
                            updatedAt = now,
                            rangePreset = draft.preset.name,
                            customStartMs = draft.customStartMs,
                            customEndMs = draft.customEndMs,
                            maxItems = draft.maxItems,
                            includeSms = draft.includeSms,
                            includeNotifications = draft.includeNotifications,
                            contextMode = draft.contextMode.name
                        )
                    )
                }
            }

            if (question.isNullOrBlank()) return@launch

            _isAiSending.value = true
            try {
                val contextResult = xmlBuilder.buildContextForAi(
                    question = question,
                    contextMode = draft.contextMode,
                    preset = draft.preset,
                    customStartMs = draft.customStartMs,
                    customEndMs = draft.customEndMs,
                    maxItems = draft.maxItems,
                    includeSms = draft.includeSms,
                    includeNotifications = draft.includeNotifications,
                    isPersian = isPersian,
                    strings = strings
                )
                val kb = String.format(java.util.Locale.US, "%.1f KB", contextResult.totalBytes / 1024.0)
                val countNum = DateUtils.formatNumber(contextResult.totalCount, isPersian)
                val modeTag = when (draft.contextMode) {
                    AiContextMode.COMPACT_TEXT -> if (isPersian) "متن فشرده" else "Compact Text"
                    AiContextMode.KEYWORD_PREFILTER -> if (isPersian) "فیلتر FTS" else "FTS Digest"
                    AiContextMode.INLINE_XML -> "XML"
                }
                val summaryLabel = if (isPersian) {
                    "${contextResult.rangeLabel} • $countNum مورد • $kb $modeTag"
                } else {
                    "${contextResult.rangeLabel} • $countNum items • $kb $modeTag"
                }

                val priorHistory = aiTopicDao.getMessagesForTopicOnce(topicId)

                aiTopicDao.insertChatMessage(
                    AiChatMessageEntity(
                        topicId = topicId,
                        role = "USER",
                        text = question,
                        xmlSummaryLabel = summaryLabel,
                        xmlContent = contextResult.xmlContent,
                        timestamp = System.currentTimeMillis()
                    )
                )

                val settings = settingsRepo.getCurrentSettings()
                val aiResult = aiClient.askWithSearchContext(
                    settings = settings,
                    userQuestion = question,
                    contextPayload = contextResult.xmlContent,
                    contextMode = draft.contextMode,
                    priorHistory = priorHistory,
                    isPersian = isPersian
                )

                aiResult.onSuccess { parsed ->
                    aiTopicDao.insertChatMessage(
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
                    val errMsg = err.localizedMessage ?: err.message ?: "AI request failed"
                    aiTopicDao.insertChatMessage(
                        AiChatMessageEntity(
                            topicId = topicId,
                            role = "ASSISTANT",
                            text = errMsg,
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                    )
                }

                aiTopicDao.getTopicById(topicId)?.let { t ->
                    aiTopicDao.updateTopic(t.copy(updatedAt = System.currentTimeMillis()))
                }
            } finally {
                _isAiSending.value = false
            }
        }
    }
}
