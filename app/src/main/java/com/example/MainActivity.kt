package com.example

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.drive.GoogleDriveAuthHelper
import com.example.data.drive.PkceSession
import com.google.android.gms.auth.api.identity.Identity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.compat.AndroidCompat
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.core.localization.resolveIsPersian
import com.example.data.search.ArchiveItemSource
import com.example.data.search.SearchResultItem
import com.example.ui.components.SmsPermissionRationaleDialog
import com.example.ui.components.SmsPermissionSettingsDialog
import com.example.ui.navigation.AIChatRoute
import com.example.ui.navigation.MessagesRoute
import com.example.ui.navigation.NotificationsRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.ThreadRoute
import com.example.ui.screens.AIChatScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SearchAndAiScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ThreadScreen
import com.example.ui.theme.AiSmsManagerTheme
import com.example.ui.viewmodel.AiChatViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SearchAndAiViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_THREAD_ID = "extra_open_thread_id"
        const val EXTRA_OPEN_ADDRESS = "extra_open_address"
        const val EXTRA_OPEN_MESSAGE_ID = "extra_open_message_id"
    }

    private var launchIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchIntentState = intent
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            val calendarMode by settingsVm.calendarMode.collectAsStateWithLifecycle()
            val isPersian = settings.language.resolveIsPersian()

            AiSmsManagerTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                isPersian = isPersian,
                calendarMode = calendarMode
            ) {
                AiSmsManagerRoot(
                    settingsVm = settingsVm,
                    pendingIntent = launchIntentState,
                    onConsumeIntent = { launchIntentState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntentState = intent
    }
}

@Composable
private fun AiSmsManagerRoot(
    settingsVm: SettingsViewModel,
    pendingIntent: Intent?,
    onConsumeIntent: () -> Unit
) {
    val mainVm: MainViewModel = viewModel()
    val searchAiVm: SearchAndAiViewModel = viewModel()
    val aiChatVm: AiChatViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current

    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val testState by settingsVm.testState.collectAsStateWithLifecycle()
    val modelsState by settingsVm.modelsState.collectAsStateWithLifecycle()
    val driveState by settingsVm.driveState.collectAsStateWithLifecycle()
    val conversations by mainVm.conversations.collectAsStateWithLifecycle()
    val contacts by mainVm.contacts.collectAsStateWithLifecycle()
    val notifications by mainVm.notifications.collectAsStateWithLifecycle()
    val isSyncing by mainVm.isSyncing.collectAsStateWithLifecycle()
    val systemStatus by mainVm.systemStatus.collectAsStateWithLifecycle()
    val isSmsSearchExpanded by mainVm.isSmsSearchExpanded.collectAsStateWithLifecycle()
    val smsSearchQuery by mainVm.smsSearchQuery.collectAsStateWithLifecycle()
    val smsSearchResults by mainVm.smsSearchResults.collectAsStateWithLifecycle()

    val filterState by searchAiVm.filterState.collectAsStateWithLifecycle()
    val localResults by searchAiVm.localResults.collectAsStateWithLifecycle()
    val topics by searchAiVm.topics.collectAsStateWithLifecycle()
    val activeTopicId by searchAiVm.activeTopicId.collectAsStateWithLifecycle()
    val activeTopic by searchAiVm.activeTopic.collectAsStateWithLifecycle()
    val chatMessages by searchAiVm.chatMessages.collectAsStateWithLifecycle()
    val isAiSending by searchAiVm.isAiSending.collectAsStateWithLifecycle()
    val showScopeSheet by searchAiVm.showScopeSheet.collectAsStateWithLifecycle()
    val pendingQuestion by searchAiVm.pendingQuestion.collectAsStateWithLifecycle()
    val scopeDraft by searchAiVm.scopeDraft.collectAsStateWithLifecycle()
    val draftCounts by searchAiVm.draftCounts.collectAsStateWithLifecycle()
    val xmlPreview by searchAiVm.xmlPreview.collectAsStateWithLifecycle()
    val recentSearches by searchAiVm.recentSearches.collectAsStateWithLifecycle()
    val searchHistory by searchAiVm.searchHistory.collectAsStateWithLifecycle()
    val aiChatArchive by searchAiVm.aiChatArchive.collectAsStateWithLifecycle()
    val selectedArchivedSearch by searchAiVm.selectedArchivedSearch.collectAsStateWithLifecycle()
    val selectedArchivedAiChat by searchAiVm.selectedArchivedAiChat.collectAsStateWithLifecycle()
    val searchSavedTick by searchAiVm.searchSavedTick.collectAsStateWithLifecycle()

    val showDefaultSmsBanner by mainVm.shouldShowDefaultSmsBanner.collectAsStateWithLifecycle()

    var selectedNotificationDetail by remember { mutableStateOf<SearchResultItem?>(null) }
    var showSmsRationaleDialog by remember { mutableStateOf(false) }
    var showSmsSettingsDialog by remember { mutableStateOf(false) }
    var hasPromptedSmsPermissionsOnce by remember { mutableStateOf(false) }
    var pendingPkceSession by remember { mutableStateOf<PkceSession?>(null) }
    var pendingFallbackAccountEmail by remember { mutableStateOf<String?>(null) }
    val activity = remember(context) { AndroidCompat.findActivity(context) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPromptedSmsPermissionsOnce = true
        mainVm.syncAllIfPermitted()
        val hasCoreSms = AndroidCompat.hasCoreSmsPermissions(context)
        if (!hasCoreSms && results.isNotEmpty()) {
            if (AndroidCompat.shouldShowSmsRationale(activity)) {
                showSmsRationaleDialog = true
            } else {
                showSmsSettingsDialog = true
            }
        }
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        mainVm.syncAllIfPermitted()
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri == null) {
                settingsVm.setSmsRingtone(AndroidCompat.RINGTONE_SILENT_VALUE, strings.ringtoneSilent)
                AndroidCompat.ensureSmsNotificationChannel(
                    context = context,
                    title = strings.tabMessages,
                    channelVersion = settings.notificationChannelVersion + 1,
                    ringtoneUriString = AndroidCompat.RINGTONE_SILENT_VALUE,
                    vibrate = settings.notificationVibrate
                )
            } else {
                val uriStr = pickedUri.toString()
                val title = AndroidCompat.resolveRingtoneTitle(context, uriStr, isPersian)
                settingsVm.setSmsRingtone(uriStr, title)
                AndroidCompat.ensureSmsNotificationChannel(
                    context = context,
                    title = strings.tabMessages,
                    channelVersion = settings.notificationChannelVersion + 1,
                    ringtoneUriString = uriStr,
                    vibrate = settings.notificationVibrate
                )
            }
        }
    }

    val driveRecoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val email = pendingFallbackAccountEmail
        if (res.resultCode == Activity.RESULT_OK && !email.isNullOrBlank()) {
            settingsVm.connectDriveWithGoogleAccount(email, isPersian) {
                settingsVm.onDriveAuthFailed(
                    if (isPersian) "تایید مجدد Google Drive تکمیل نشد." else "Google Drive consent incomplete."
                )
            }
        } else if (res.resultCode != Activity.RESULT_OK) {
            settingsVm.onDriveAuthFailed(
                if (isPersian) "تایید دسترسی Google Drive لغو شد." else "Google Drive authorization cancelled."
            )
        }
    }

    val driveAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val email = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
            if (email.isNotBlank()) {
                pendingFallbackAccountEmail = email
                settingsVm.connectDriveWithGoogleAccount(email, isPersian) { recoverIntent ->
                    driveRecoverLauncher.launch(recoverIntent)
                }
            }
        } else {
            settingsVm.setDriveTroubleshootingVisible(true)
        }
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                settingsVm.onDriveAuthorized(token, isPersian)
            } else {
                settingsVm.onDriveAuthFailed(
                    if (isPersian) "توکن دسترسی Google Drive دریافت نشد." else "Google Drive access token was empty."
                )
            }
        } catch (e: Exception) {
            settingsVm.onDriveAuthException(e, isPersian)
            val (_, isConfigErr) = GoogleDriveAuthHelper.describeDriveAuthException(context, e, isPersian)
            if (isConfigErr) {
                driveAccountPickerLauncher.launch(
                    GoogleDriveAuthHelper.createGoogleAccountPickerIntent(driveState.accountEmail)
                )
            }
        }
    }

    val connectGoogleDrive = {
        val targetActivity = activity
        if (targetActivity == null) {
            settingsVm.onDriveAuthFailed("Activity unavailable")
        } else {
            Identity.getAuthorizationClient(targetActivity)
                .authorize(settingsVm.buildDriveAuthorizationRequest())
                .addOnSuccessListener { result ->
                    val pendingIntent = result.pendingIntent
                    if (result.hasResolution() && pendingIntent != null) {
                        driveAuthLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        val token = result.accessToken
                        if (!token.isNullOrBlank()) {
                            settingsVm.onDriveAuthorized(token, isPersian)
                        } else {
                            settingsVm.onDriveAuthFailed(
                                if (isPersian) "توکن Google Drive دریافت نشد." else "No access token returned."
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    settingsVm.onDriveAuthException(e, isPersian)
                    val (_, isConfigErr) = GoogleDriveAuthHelper.describeDriveAuthException(context, e, isPersian)
                    if (isConfigErr) {
                        driveAccountPickerLauncher.launch(
                            GoogleDriveAuthHelper.createGoogleAccountPickerIntent(driveState.accountEmail)
                        )
                    }
                }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = settingsVm.exportLocalBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }.onSuccess {
                    settingsVm.setLocalDriveMessage(
                        if (isPersian) "فایل بکاپ با موفقیت ذخیره شد." else "Backup JSON saved."
                    )
                }.onFailure { err ->
                    settingsVm.setLocalDriveMessage(
                        err.localizedMessage ?: "Export failed",
                        isError = true
                    )
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: error("Cannot read selected file")
                }.onSuccess { rawJson ->
                    settingsVm.importLocalBackupJson(rawJson, isPersian)
                }.onFailure { err ->
                    settingsVm.setLocalDriveMessage(
                        err.localizedMessage ?: "Import failed",
                        isError = true
                    )
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainVm.refreshSystemStatuses()
                settingsVm.triggerAutoBackupIfDue()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle external sms:/smsto:, OAuth PKCE redirect, or notification click intents
    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent ?: return@LaunchedEffect
        val dataUri = intent.data
        if (intent.action == Intent.ACTION_VIEW &&
            dataUri != null &&
            dataUri.scheme == context.packageName &&
            dataUri.path == "/oauth2redirect"
        ) {
            val session = pendingPkceSession
            if (session != null) {
                settingsVm.completeBrowserPkce(session, dataUri.toString(), isPersian)
                pendingPkceSession = null
            }
            onConsumeIntent()
            return@LaunchedEffect
        }

        val threadIdExtra = intent.getLongExtra(MainActivity.EXTRA_OPEN_THREAD_ID, -1L)
        val addressExtra = intent.getStringExtra(MainActivity.EXTRA_OPEN_ADDRESS)
        val msgIdExtra = intent.getLongExtra(MainActivity.EXTRA_OPEN_MESSAGE_ID, -1L)

        if (threadIdExtra > 0L && !addressExtra.isNullOrBlank()) {
            navController.navigate(ThreadRoute(threadIdExtra, addressExtra, msgIdExtra))
            onConsumeIntent()
        } else if (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND) {
            val recipient = intent.data?.schemeSpecificPart?.trim().orEmpty()
            if (recipient.isNotEmpty()) {
                val resolvedThread = mainVm.resolveThreadForAddress(recipient)
                navController.navigate(ThreadRoute(resolvedThread, recipient, -1L))
            }
            onConsumeIntent()
        }
    }

    val launchRingtonePicker = {
        val existingUri: Uri? = when {
            settings.smsRingtoneUri == AndroidCompat.RINGTONE_SILENT_VALUE -> null
            settings.smsRingtoneUri.isNotBlank() -> runCatching { Uri.parse(settings.smsRingtoneUri) }.getOrNull()
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, strings.smsRingtoneLabel)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
        }
        ringtonePickerLauncher.launch(pickerIntent)
    }

    val launchSystemPermissionPrompt = {
        permissionsLauncher.launch(AndroidCompat.requiredMessagingPermissions())
    }

    val requestAllNeededPermissions = {
        if (!AndroidCompat.hasCoreSmsPermissions(context)) {
            if (AndroidCompat.shouldShowSmsRationale(activity) || !hasPromptedSmsPermissionsOnce) {
                showSmsRationaleDialog = true
            } else {
                launchSystemPermissionPrompt()
            }
        } else {
            launchSystemPermissionPrompt()
        }
    }

    val requestDefaultSmsRole = {
        defaultSmsLauncher.launch(AndroidCompat.createDefaultSmsIntent(context))
    }

    val openNotificationAccessSettings = {
        context.startActivity(
            AndroidCompat.createNotificationListenerSettingsIntent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    val onSearchItemClicked: (SearchResultItem) -> Unit = { item ->
        if (item.source == ArchiveItemSource.SMS && item.threadId != null) {
            navController.navigate(
                ThreadRoute(
                    threadId = item.threadId,
                    address = item.senderSecondary,
                    targetMessageId = item.itemId
                )
            )
        } else {
            selectedNotificationDetail = item
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isInThread = currentDest?.hasRoute<ThreadRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        val navigateTopTab: (Any) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        val navHostContent: @Composable (Modifier) -> Unit = { mod ->
            NavHost(
                navController = navController,
                startDestination = MessagesRoute,
                modifier = mod
            ) {
                composable<MessagesRoute> {
                    MessagesScreen(
                        conversations = conversations,
                        contacts = contacts,
                        systemStatus = systemStatus,
                        showDefaultSmsBanner = showDefaultSmsBanner,
                        isSyncing = isSyncing,
                        isSearchExpanded = isSmsSearchExpanded,
                        searchQuery = smsSearchQuery,
                        searchResults = smsSearchResults,
                        onSearchExpandedChange = mainVm::setSmsSearchExpanded,
                        onSearchQueryChange = mainVm::updateSmsSearchQuery,
                        onSearchSubmit = mainVm::saveCurrentSmsSearchToArchive,
                        onRequestPermissions = requestAllNeededPermissions,
                        onRequestDefaultSms = requestDefaultSmsRole,
                        onDismissDefaultSmsBanner = mainVm::dismissDefaultSmsBannerToday,
                        onSyncSms = { mainVm.syncAllIfPermitted() },
                        onOpenThread = { threadId, address, targetMsgId ->
                            navController.navigate(ThreadRoute(threadId, address, targetMsgId))
                        },
                        onStartChatWithAddress = { address ->
                            scope.launch {
                                val tid = mainVm.resolveThreadForAddress(address)
                                navController.navigate(ThreadRoute(tid, address, -1L))
                            }
                        },
                        onTogglePinThread = mainVm::setThreadPinned,
                        onDeleteThread = { mainVm.deleteThread(it) }
                    )
                }

                composable<SearchRoute> {
                    SearchAndAiScreen(
                        filterState = filterState,
                        localResults = localResults,
                        recentSearches = recentSearches,
                        searchHistory = searchHistory,
                        aiChatArchive = aiChatArchive,
                        selectedArchivedSearch = selectedArchivedSearch,
                        selectedArchivedAiChat = selectedArchivedAiChat,
                        searchSavedTick = searchSavedTick,
                        isAiConfigured = settings.isAiConfigured,
                        topics = topics,
                        activeTopicId = activeTopicId,
                        activeTopic = activeTopic,
                        chatMessages = chatMessages,
                        isAiSending = isAiSending,
                        showScopeSheet = showScopeSheet,
                        pendingQuestion = pendingQuestion,
                        scopeDraft = scopeDraft,
                        draftCounts = draftCounts,
                        xmlPreview = xmlPreview,
                        onQueryChange = searchAiVm::updateQuery,
                        onSortChange = searchAiVm::updateSortOrder,
                        onSourceChange = searchAiVm::updateSourceFilter,
                        onDirectionChange = searchAiVm::updateDirectionFilter,
                        onDateRangeChange = searchAiVm::updateLocalDateRange,
                        onClearDateRange = searchAiVm::clearLocalDateRange,
                        onSaveSearchNow = { searchAiVm.saveCurrentSearchNow() },
                        onOpenArchivedSearch = searchAiVm::openArchivedSearch,
                        onDismissArchivedSearch = searchAiVm::dismissArchivedSearch,
                        onRerunArchivedSearch = searchAiVm::rerunArchivedSearch,
                        onDeleteArchivedSearch = searchAiVm::deleteArchivedSearch,
                        onClearSearchHistory = searchAiVm::clearSearchHistory,
                        onOpenArchivedAiChat = searchAiVm::openArchivedAiChat,
                        onDismissArchivedAiChat = searchAiVm::dismissArchivedAiChat,
                        onOpenTopicInAiChatScreen = { topicId ->
                            navController.navigate(AIChatRoute(topicId = topicId))
                        },
                        onSelectTopic = searchAiVm::selectTopic,
                        onNewTopic = searchAiVm::startNewTopic,
                        onDeleteTopic = searchAiVm::deleteTopic,
                        onAskQuestion = searchAiVm::requestScopeAndSend,
                        onOpenScopeEditor = searchAiVm::openScopeEditorOnly,
                        onDismissScopeSheet = searchAiVm::dismissScopeSheet,
                        onUpdateScopeDraft = searchAiVm::updateScopeDraft,
                        onPreviewXml = { searchAiVm.generateXmlPreview(isPersian, strings) },
                        onShowExistingXml = searchAiVm::showExistingXml,
                        onDismissXmlPreview = searchAiVm::dismissXmlPreview,
                        onConfirmScopeAndSend = { searchAiVm.confirmScopeAndSendIfPending(isPersian, strings) },
                        onOpenSettings = { navigateTopTab(SettingsRoute) },
                        onSearchResultClick = onSearchItemClicked
                    )
                }

                composable<NotificationsRoute> {
                    NotificationsScreen(
                        notifications = notifications,
                        isListenerEnabled = systemStatus.isNotificationListenerEnabled,
                        isCaptureEnabledInSettings = settings.captureNotifications,
                        onOpenListenerSettings = openNotificationAccessSettings,
                        onOpenAppSettings = { navigateTopTab(SettingsRoute) },
                        onDeleteNotification = mainVm::deleteNotification,
                        onClearAll = mainVm::clearAllNotifications,
                        onAskAiAboutNotifications = {
                            navController.navigate(AIChatRoute(source = "NOTIFICATIONS", threadId = -1L))
                        }
                    )
                }

                composable<AIChatRoute> { backStack ->
                    val route = backStack.toRoute<AIChatRoute>()
                    LaunchedEffect(route.source, route.threadId, route.topicId) {
                        if (route.topicId > 0L || route.source.isNotBlank() || route.threadId > 0L) {
                            aiChatVm.applyInitialRouteContext(route.source, route.threadId, route.topicId)
                        }
                    }

                    val aiContextConfig by aiChatVm.contextConfig.collectAsStateWithLifecycle()
                    val aiConversations by aiChatVm.conversations.collectAsStateWithLifecycle()
                    val aiNotifApps by aiChatVm.notificationApps.collectAsStateWithLifecycle()
                    val aiNotifCount by aiChatVm.totalNotificationCount.collectAsStateWithLifecycle()
                    val aiMessages by aiChatVm.messages.collectAsStateWithLifecycle()
                    val aiArchivedSessions by aiChatVm.archivedSessions.collectAsStateWithLifecycle()
                    val aiActiveTopicId by aiChatVm.activeSessionTopicId.collectAsStateWithLifecycle()
                    val aiSending by aiChatVm.isSending.collectAsStateWithLifecycle()
                    val aiXmlPreview by aiChatVm.xmlPreview.collectAsStateWithLifecycle()

                    AIChatScreen(
                        isAiConfigured = settings.isAiConfigured,
                        aiContextLimitTokens = settings.aiContextLimitTokens,
                        aiSupportsFileUpload = settings.aiSupportsFileUpload,
                        contextConfig = aiContextConfig,
                        conversations = aiConversations,
                        notificationApps = aiNotifApps,
                        totalNotificationCount = aiNotifCount,
                        messages = aiMessages,
                        archivedSessions = aiArchivedSessions,
                        activeTopicId = aiActiveTopicId,
                        isSending = aiSending,
                        xmlPreview = aiXmlPreview,
                        onSelectSource = aiChatVm::selectSource,
                        onSelectThread = aiChatVm::selectThread,
                        onSelectNotificationApp = aiChatVm::selectNotificationApp,
                        onSelectRangePreset = aiChatVm::selectRangePreset,
                        onToggleSmartContext = aiChatVm::toggleSmartContext,
                        onNewChat = aiChatVm::startNewSession,
                        onLoadArchivedSession = aiChatVm::loadArchivedSession,
                        onDeleteArchivedSession = aiChatVm::deleteArchivedSession,
                        onPreviewCurrentXml = { q ->
                            aiChatVm.previewCurrentXmlPrompt(q, isPersian, strings)
                        },
                        onShowStoredXml = aiChatVm::showStoredXml,
                        onDismissXmlPreview = aiChatVm::dismissXmlPreview,
                        onSendMessage = { q ->
                            aiChatVm.sendMessage(q, isPersian, strings)
                        },
                        onOpenSettings = { navigateTopTab(SettingsRoute) },
                        onMatchedItemClick = onSearchItemClicked
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        systemStatus = systemStatus,
                        testState = testState,
                        modelsState = modelsState,
                        driveState = driveState,
                        apkPackageName = settingsVm.apkPackageName,
                        apkSha1Fingerprint = settingsVm.apkSha1Fingerprint,
                        onLanguageChange = settingsVm::setLanguage,
                        onCalendarPreferenceChange = settingsVm::setCalendarPreference,
                        onThemeModeChange = settingsVm::setThemeMode,
                        onDynamicColorChange = settingsVm::setDynamicColor,
                        onCaptureNotificationsChange = settingsVm::setCaptureNotifications,
                        onSmsNotificationsChange = settingsVm::setSmsNotificationsEnabled,
                        onNotificationPreviewChange = settingsVm::setShowNotificationPreviewText,
                        onNotificationVibrateChange = { enabled ->
                            settingsVm.setNotificationVibrate(enabled)
                            AndroidCompat.ensureSmsNotificationChannel(
                                context = context,
                                title = strings.tabMessages,
                                channelVersion = settings.notificationChannelVersion + 1,
                                ringtoneUriString = settings.smsRingtoneUri,
                                vibrate = enabled
                            )
                        },
                        onPickSmsRingtone = launchRingtonePicker,
                        onOpenSmsChannelSettings = {
                            AndroidCompat.ensureSmsNotificationChannel(
                                context = context,
                                title = strings.tabMessages,
                                channelVersion = settings.notificationChannelVersion,
                                ringtoneUriString = settings.smsRingtoneUri,
                                vibrate = settings.notificationVibrate
                            )
                            context.startActivity(
                                AndroidCompat.createSmsChannelSettingsIntent(
                                    context,
                                    settings.notificationChannelVersion
                                )
                            )
                        },
                        onRequestDefaultSms = requestDefaultSmsRole,
                        onOpenNotificationListenerSettings = openNotificationAccessSettings,
                        onSaveAiConfig = settingsVm::saveAiConfig,
                        onTestAiConnection = { p, u, m, k, ctx ->
                            settingsVm.testAiConnection(p, u, m, k, ctx, isPersian)
                        },
                        onFetchModels = { p, u, k ->
                            settingsVm.fetchModels(p, u, k, isPersian)
                        },
                        onClearAiConfig = settingsVm::clearAiConfig,
                        onBackupScopeChange = settingsVm::setBackupScope,
                        onBackupSyncModeChange = settingsVm::setBackupSyncMode,
                        onConnectGoogleDrive = { connectGoogleDrive() },
                        onConnectDriveFallbackAccount = {
                            driveAccountPickerLauncher.launch(
                                GoogleDriveAuthHelper.createGoogleAccountPickerIntent(driveState.accountEmail)
                            )
                        },
                        onLaunchDriveBrowserPkce = { clientId ->
                            val session = settingsVm.startBrowserPkceSession(clientId)
                            if (session != null) {
                                pendingPkceSession = session
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(session.authUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }.onFailure { err ->
                                    settingsVm.onDriveAuthFailed(err.localizedMessage ?: "Cannot launch browser")
                                }
                            }
                        },
                        onCompleteDriveBrowserPkce = { raw ->
                            val session = pendingPkceSession
                            if (session != null) {
                                settingsVm.completeBrowserPkce(session, raw, isPersian)
                                pendingPkceSession = null
                            } else {
                                settingsVm.onDriveAuthFailed(
                                    if (isPersian) "ابتدا ورود از طریق مرورگر را آغاز کنید." else "Start browser login first."
                                )
                            }
                        },
                        onConnectDriveWithToken = { token ->
                            settingsVm.connectWithManualAccessToken(token, isPersian)
                        },
                        onToggleDriveTroubleshooting = settingsVm::setDriveTroubleshootingVisible,
                        onDisconnectGoogleDrive = { settingsVm.disconnectDrive(isPersian) },
                        onRefreshDriveBackups = { settingsVm.refreshDriveBackups(isPersian) },
                        onBackupToGoogleDrive = { settingsVm.backupToGoogleDrive(isPersian) },
                        onRestoreFromGoogleDrive = { fileId ->
                            settingsVm.restoreFromGoogleDrive(fileId, isPersian)
                        },
                        onDeleteDriveBackup = { fileId ->
                            settingsVm.deleteDriveBackup(fileId, isPersian)
                        },
                        onExportLocalBackup = {
                            exportBackupLauncher.launch("ai_sms_backup_${System.currentTimeMillis()}.json")
                        },
                        onImportLocalBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                        }
                    )
                }

                composable<ThreadRoute> { backStack ->
                    val route = backStack.toRoute<ThreadRoute>()
                    val threadMessages by mainVm.observeThreadMessages(route.threadId)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val pinnedMessages by mainVm.observePinnedMessagesInThread(route.threadId)
                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    ThreadScreen(
                        threadId = route.threadId,
                        address = route.address,
                        targetMessageId = route.targetMessageId,
                        messages = threadMessages,
                        pinnedMessages = pinnedMessages,
                        hasSendSmsPermission = systemStatus.hasSendSmsPermission,
                        onRequestSmsPermissions = requestAllNeededPermissions,
                        onBack = { navController.popBackStack() },
                        onMarkRead = { mainVm.markThreadRead(route.threadId) },
                        onSendSms = { text ->
                            mainVm.sendSms(route.threadId, route.address, text)
                        },
                        onTogglePinMessage = { msg, pin ->
                            mainVm.setMessagePinned(msg.id, pin)
                        },
                        onDeleteMessage = mainVm::deleteMessage,
                        onSaveContact = { addr ->
                            context.startActivity(mainVm.buildSaveContactIntent(addr))
                        },
                        onAskAiAboutThread = {
                            navController.navigate(AIChatRoute(source = "THREAD", threadId = route.threadId))
                        }
                    )
                }
            }
        }

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (!isInThread) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<MessagesRoute>() == true,
                            onClick = { navigateTopTab(MessagesRoute) },
                            icon = {
                                Icon(
                                    if (currentDest?.hasRoute<MessagesRoute>() == true) Icons.Filled.Sms else Icons.Outlined.Sms,
                                    contentDescription = strings.tabMessages
                                )
                            },
                            label = { Text(strings.tabMessages) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<SearchRoute>() == true,
                            onClick = { navigateTopTab(SearchRoute) },
                            icon = {
                                Icon(
                                    if (currentDest?.hasRoute<SearchRoute>() == true) Icons.Filled.Search else Icons.Outlined.Search,
                                    contentDescription = strings.tabSearch
                                )
                            },
                            label = { Text(strings.tabSearch) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<AIChatRoute>() == true,
                            onClick = { navigateTopTab(AIChatRoute()) },
                            icon = {
                                Icon(
                                    if (currentDest?.hasRoute<AIChatRoute>() == true) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = strings.tabAiChat
                                )
                            },
                            label = { Text(strings.tabAiChat) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<NotificationsRoute>() == true,
                            onClick = { navigateTopTab(NotificationsRoute) },
                            icon = {
                                Icon(
                                    if (currentDest?.hasRoute<NotificationsRoute>() == true) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = strings.tabNotifications
                                )
                            },
                            label = { Text(strings.tabNotifications) }
                        )
                        NavigationRailItem(
                            selected = currentDest?.hasRoute<SettingsRoute>() == true,
                            onClick = { navigateTopTab(SettingsRoute) },
                            icon = {
                                Icon(
                                    if (currentDest?.hasRoute<SettingsRoute>() == true) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = strings.tabSettings
                                )
                            },
                            label = { Text(strings.tabSettings) }
                        )
                    }
                }
                navHostContent(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isInThread) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentDest?.hasRoute<MessagesRoute>() == true,
                                onClick = { navigateTopTab(MessagesRoute) },
                                icon = {
                                    Icon(
                                        if (currentDest?.hasRoute<MessagesRoute>() == true) Icons.Filled.Sms else Icons.Outlined.Sms,
                                        contentDescription = strings.tabMessages
                                    )
                                },
                                label = { Text(strings.tabMessages) },
                                modifier = Modifier.testTag("nav_messages_tab")
                            )
                            NavigationBarItem(
                                selected = currentDest?.hasRoute<SearchRoute>() == true,
                                onClick = { navigateTopTab(SearchRoute) },
                                icon = {
                                    Icon(
                                        if (currentDest?.hasRoute<SearchRoute>() == true) Icons.Filled.Search else Icons.Outlined.Search,
                                        contentDescription = strings.tabSearch
                                    )
                                },
                                label = { Text(strings.tabSearch) },
                                modifier = Modifier.testTag("nav_search_tab")
                            )
                            NavigationBarItem(
                                selected = currentDest?.hasRoute<AIChatRoute>() == true,
                                onClick = { navigateTopTab(AIChatRoute()) },
                                icon = {
                                    Icon(
                                        if (currentDest?.hasRoute<AIChatRoute>() == true) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                        contentDescription = strings.tabAiChat
                                    )
                                },
                                label = { Text(strings.tabAiChat) },
                                modifier = Modifier.testTag("nav_aichat_tab")
                            )
                            NavigationBarItem(
                                selected = currentDest?.hasRoute<NotificationsRoute>() == true,
                                onClick = { navigateTopTab(NotificationsRoute) },
                                icon = {
                                    Icon(
                                        if (currentDest?.hasRoute<NotificationsRoute>() == true) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = strings.tabNotifications
                                    )
                                },
                                label = { Text(strings.tabNotifications) },
                                modifier = Modifier.testTag("nav_notifications_tab")
                            )
                            NavigationBarItem(
                                selected = currentDest?.hasRoute<SettingsRoute>() == true,
                                onClick = { navigateTopTab(SettingsRoute) },
                                icon = {
                                    Icon(
                                        if (currentDest?.hasRoute<SettingsRoute>() == true) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = strings.tabSettings
                                    )
                                },
                                label = { Text(strings.tabSettings) },
                                modifier = Modifier.testTag("nav_settings_tab")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    navHostContent(Modifier.fillMaxSize())
                }
            }
        }
    }

    selectedNotificationDetail?.let { notifItem ->
        AlertDialog(
            onDismissRequest = { selectedNotificationDetail = null },
            title = { Text("${notifItem.senderDisplayName} • ${notifItem.senderSecondary}") },
            text = {
                Text(
                    text = "${notifItem.body}\n\n${DateUtils.formatDateTime(notifItem.timestamp, calendarMode, usePersianDigits = isPersian)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedNotificationDetail = null
                        navController.navigate(NotificationsRoute)
                    }
                ) {
                    Text(strings.tabNotifications)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNotificationDetail = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showSmsRationaleDialog) {
        SmsPermissionRationaleDialog(
            strings = strings,
            onConfirm = {
                showSmsRationaleDialog = false
                launchSystemPermissionPrompt()
            },
            onDismiss = { showSmsRationaleDialog = false }
        )
    }

    if (showSmsSettingsDialog) {
        SmsPermissionSettingsDialog(
            strings = strings,
            onOpenSettings = {
                showSmsSettingsDialog = false
                context.startActivity(AndroidCompat.createAppDetailsSettingsIntent(context))
            },
            onDismiss = { showSmsSettingsDialog = false }
        )
    }
}
