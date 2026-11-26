package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
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
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.core.localization.resolveIsPersian
import com.example.data.search.ArchiveItemSource
import com.example.data.search.SearchResultItem
import com.example.ui.navigation.MessagesRoute
import com.example.ui.navigation.NotificationsRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.ThreadRoute
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SearchAndAiScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ThreadScreen
import com.example.ui.theme.AiSmsManagerTheme
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
            val isPersian = settings.language.resolveIsPersian()

            AiSmsManagerTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                isPersian = isPersian
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
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current

    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val testState by settingsVm.testState.collectAsStateWithLifecycle()
    val conversations by mainVm.conversations.collectAsStateWithLifecycle()
    val contacts by mainVm.contacts.collectAsStateWithLifecycle()
    val notifications by mainVm.notifications.collectAsStateWithLifecycle()
    val isSyncing by mainVm.isSyncing.collectAsStateWithLifecycle()
    val systemStatus by mainVm.systemStatus.collectAsStateWithLifecycle()

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

    var selectedNotificationDetail by remember { mutableStateOf<SearchResultItem?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mainVm.syncAllIfPermitted()
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        mainVm.syncAllIfPermitted()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainVm.refreshSystemStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle external sms:/smsto: or notification click intents
    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent ?: return@LaunchedEffect
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

    val requestAllNeededPermissions = {
        val perms = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
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
                        isSyncing = isSyncing,
                        onRequestPermissions = requestAllNeededPermissions,
                        onRequestDefaultSms = requestDefaultSmsRole,
                        onSyncSms = { mainVm.syncAllIfPermitted() },
                        onOpenThread = { threadId, address ->
                            navController.navigate(ThreadRoute(threadId, address, -1L))
                        },
                        onStartChatWithAddress = { address ->
                            scope.launch {
                                val tid = mainVm.resolveThreadForAddress(address)
                                navController.navigate(ThreadRoute(tid, address, -1L))
                            }
                        },
                        onDeleteThread = { mainVm.deleteThread(it) }
                    )
                }

                composable<SearchRoute> {
                    SearchAndAiScreen(
                        filterState = filterState,
                        localResults = localResults,
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
                        onClearAll = mainVm::clearAllNotifications
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        systemStatus = systemStatus,
                        testState = testState,
                        onLanguageChange = settingsVm::setLanguage,
                        onThemeModeChange = settingsVm::setThemeMode,
                        onDynamicColorChange = settingsVm::setDynamicColor,
                        onCaptureNotificationsChange = settingsVm::setCaptureNotifications,
                        onRequestDefaultSms = requestDefaultSmsRole,
                        onOpenNotificationListenerSettings = openNotificationAccessSettings,
                        onSaveAiConfig = settingsVm::saveAiConfig,
                        onTestAiConnection = { p, u, m, k ->
                            settingsVm.testAiConnection(p, u, m, k, isPersian)
                        }
                    )
                }

                composable<ThreadRoute> { backStack ->
                    val route = backStack.toRoute<ThreadRoute>()
                    val threadMessages by mainVm.observeThreadMessages(route.threadId)
                        .collectAsStateWithLifecycle(initialValue = emptyList())

                    ThreadScreen(
                        threadId = route.threadId,
                        address = route.address,
                        targetMessageId = route.targetMessageId,
                        messages = threadMessages,
                        onBack = { navController.popBackStack() },
                        onMarkRead = { mainVm.markThreadRead(route.threadId) },
                        onSendSms = { text ->
                            mainVm.sendSms(route.threadId, route.address, text)
                        },
                        onDeleteMessage = mainVm::deleteMessage,
                        onSaveContact = { addr ->
                            context.startActivity(mainVm.buildSaveContactIntent(addr))
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
                    text = "${notifItem.body}\n\n${DateUtils.formatDateTime(notifItem.timestamp, isPersian)}",
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
}
