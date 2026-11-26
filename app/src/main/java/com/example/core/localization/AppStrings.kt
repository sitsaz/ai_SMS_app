package com.example.core.localization

import androidx.compose.runtime.staticCompositionLocalOf

interface AppStrings {
    val appName: String
    val tabMessages: String
    val tabSearch: String
    val tabNotifications: String
    val tabSettings: String
    val defaultSmsBannerTitle: String
    val defaultSmsBannerBody: String
    val setAsDefaultSms: String
    val smsPermissionsMissingTitle: String
    val smsPermissionsMissingBody: String
    val grantPermissions: String
    val smsRationaleTitle: String
    val smsRationaleBody: String
    val smsRationaleReadPoint: String
    val smsRationaleSendPoint: String
    val smsRationaleContactsPoint: String
    val smsPermissionDeniedTitle: String
    val smsPermissionDeniedBody: String
    val openAppPermissions: String
    val continueAction: String
    val sendSmsPermissionBanner: String
    val emptyThreadHint: String
    val syncSmsNow: String
    val syncing: String
    val newMessage: String
    val noConversationsTitle: String
    val noConversationsBody: String
    val typeMessageHint: String
    val send: String
    val unknownSender: String
    val statusSending: String
    val statusSent: String
    val statusDelivered: String
    val statusFailed: String
    val incoming: String
    val outgoing: String
    val saveContact: String
    val viewContact: String
    val deleteMessage: String
    val deleteThread: String
    val selectContactTitle: String
    val searchContactsOrNumberHint: String
    val startChatWithNumber: String
    val noContactsFound: String
    val searchModeLocal: String
    val searchModeAiTopics: String
    val searchPlaceholder: String
    val sortNewest: String
    val sortOldest: String
    val sortSenderAsc: String
    val filterAll: String
    val filterSmsOnly: String
    val filterNotificationsOnly: String
    val filterIncoming: String
    val filterOutgoing: String
    val filterDateRange: String
    val clearDateFilter: String
    val resultsCountLabel: String
    val noSearchResultsTitle: String
    val noSearchResultsBody: String
    val tapToOpenInThread: String
    val smsLabel: String
    val notificationLabel: String
    val aiTopicsTitle: String
    val newTopic: String
    val deleteTopic: String
    val noTopicsTitle: String
    val noTopicsBody: String
    val askAiHint: String
    val selectHistoryPromptTitle: String
    val selectHistoryPromptSubtitle: String
    val presetToday: String
    val presetTwoDays: String
    val presetThreeDays: String
    val presetOneWeek: String
    val presetTwoWeeks: String
    val presetOneMonth: String
    val presetOneYear: String
    val presetCustomRange: String
    val customStartDate: String
    val customEndDate: String
    val maxItemsLabel: String
    val includeSmsLabel: String
    val includeNotificationsLabel: String
    val matchedArchiveSummary: String
    val previewXml: String
    val sendToAiWithXml: String
    val xmlPreviewTitle: String
    val copyXml: String
    val copiedToClipboard: String
    val aiNotConfiguredTitle: String
    val aiNotConfiguredBody: String
    val openSettings: String
    val aiThinking: String
    val matchedReferencesTitle: String
    val changeScope: String
    val notificationsTitle: String
    val notifAccessDisabledTitle: String
    val notifAccessDisabledBody: String
    val enableNotifAccess: String
    val notifCapturePausedTitle: String
    val notifCapturePausedBody: String
    val clearAllNotifications: String
    val filterAppsHint: String
    val noNotificationsTitle: String
    val noNotificationsBody: String
    val settingsTitle: String
    val sectionLanguage: String
    val languageSystem: String
    val languageEnglish: String
    val languagePersian: String
    val calendarModeLabel: String
    val calendarAuto: String
    val calendarJalali: String
    val calendarGregorian: String
    val searchMessagesHint: String
    val closeSearch: String
    val sectionMessagingAndArchive: String
    val defaultSmsStatusActive: String
    val defaultSmsStatusInactive: String
    val captureNotificationsToggle: String
    val captureNotificationsSubtitle: String
    val systemNotificationAccess: String
    val sectionAiConfiguration: String
    val aiProviderLabel: String
    val providerOpenAiCompat: String
    val providerGeminiRest: String
    val providerCustom: String
    val aiBaseUrlLabel: String
    val aiModelLabel: String
    val aiApiKeyLabel: String
    val saveAiConfig: String
    val testAiConnection: String
    val aiConfigSaved: String
    val aiSetupHint: String
    val smartPasteConfig: String
    val smartPasteEmpty: String
    val smartPasteApplied: String
    val fetchModels: String
    val fetchedModelsLabel: String
    val clearAiConfig: String
    val aiConfigCleared: String
    val resolvedEndpointLabel: String
    val aiContextModeLabel: String
    val contextCompactText: String
    val contextKeywordPrefilter: String
    val contextInlineXml: String
    val contextNoUploadHint: String
    val aiSummaryLabel: String
    val aiMatchReasonPrefix: String
    val suggestedFollowUpsLabel: String
    val sectionGoogleDriveBackup: String
    val driveConnectButton: String
    val driveDisconnectButton: String
    val driveConnectedLabel: String
    val driveNotConnectedHint: String
    val backupScopeLabel: String
    val backupNowButton: String
    val refreshDriveBackups: String
    val restoreButton: String
    val exportLocalBackup: String
    val importLocalBackup: String
    val driveBackupsEmpty: String
    val lastBackupLabel: String
    val backupSyncModeLabel: String
    val backupModeManual: String
    val backupModeAutomatic: String
    val backupModeAutoSubtitle: String
    val backupModeManualSubtitle: String
    val tabAiChat: String
    val aiChatTitle: String
    val sourceSmsThread: String
    val sourceNotificationHistory: String
    val sourceCombinedArchive: String
    val notificationHistoryLabel: String
    val selectSmsThreadTitle: String
    val selectSmsThreadHint: String
    val noThreadSelected: String
    val allAppsFilter: String
    val autoXmlPromptBadge: String
    val askAiAboutThread: String
    val askAiAboutNotifications: String
    val quickSuggestSummarize: String
    val quickSuggestOtpAmounts: String
    val quickSuggestDates: String
    val quickSuggestNotifSummary: String
    val quickSuggestNotifFinance: String
    val aiChatEmptyTitle: String
    val aiChatEmptyBody: String
    val newChat: String
    val openAiChatScreen: String
    val sectionAppearance: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val dynamicColorToggle: String
    val dynamicColorSubtitle: String
    val sectionAbout: String
    val versionLabel: String
    val cancel: String
    val apply: String
    val confirm: String
    val back: String
    val yearLabel: String
    val monthLabel: String
    val dayLabel: String
    val copyMessage: String
    val copyLink: String
    val linksLabel: String
    val openLink: String
    val openNotificationApp: String
    val pinThread: String
    val unpinThread: String
    val pinnedThreadsBadge: String
    val pinMessage: String
    val unpinMessage: String
    val pinnedMessagesBanner: String
    val viewAllPinned: String
    val noPinnedMessages: String
    val jumpToMessage: String
    val dismissForToday: String
    val aiContextLimitLabel: String
    val aiCapabilitiesLabel: String
    val capabilityFileSupported: String
    val capabilityFileUnsupported: String
    val capabilityImageSupported: String
    val capabilityImageUnsupported: String
    val capabilityDetectedCtx: String
    val contextSmartAuto: String
    val contextSmartAutoDesc: String
    val smartFileUploadedBadge: String
    val smartBudgetLastNBadge: String
    val sectionNotificationSettings: String
    val incomingSmsNotifToggle: String
    val incomingSmsNotifSubtitle: String
    val notifPreviewTextToggle: String
    val notifPreviewTextSubtitle: String
    val notifVibrateToggle: String
    val smsRingtoneLabel: String
    val openSystemChannelSettings: String
    val ringtoneDefault: String
    val ringtoneSilent: String
    val driveTroubleshootingTitle: String
    val driveFallbackAccountButton: String
    val driveSha1Label: String
    val drivePackageLabel: String
    val driveCopyDiagInfo: String
    val driveCustomClientIdLabel: String
    val driveBrowserLoginButton: String
    val drivePasteTokenLabel: String
    val driveConnectWithTokenButton: String
    val driveDiagnosticsHint: String
    val tabArchive: String
    val archiveTitle: String
    val archiveFilterAll: String
    val archiveFilterSearches: String
    val archiveFilterAiChats: String
    val archiveSearchHint: String
    val saveSearchAction: String
    val searchSavedToast: String
    val recentSearchesLabel: String
    val savedAiChatsLabel: String
    val noArchiveItemsTitle: String
    val noArchiveItemsBody: String
    val clearSearchHistory: String
    val rerunSearch: String
    val continueAiChat: String
    val archivedResultsCountLabel: String
    val messagesCountLabel: String
    val matchedItemsBadge: String
    val originSearchTab: String
    val originSmsBar: String
    val originAiChatTab: String
    val originAiTopicsTab: String
    val archivedSnapshotNote: String
    val threadAiHistoryBanner: String
    val noThreadAiHistory: String
    val markAllAsRead: String
    val allMarkedAsRead: String
    val openArchiveButton: String
    val exportConversation: String
    val exportAsText: String
    val exportAsJson: String
    val exportCompletedToast: String
    val aiMaxOutputTokensLabel: String
    val capabilitySystemSupported: String
    val capabilitySystemUnsupported: String
    val capabilityTempSupported: String
    val capabilityTempUnsupported: String
    val capabilityJsonSupported: String
    val aiConstraintsAppliedNote: String
    val driveLoopbackPkceButton: String
    val driveLoopbackHint: String
}

private object EnglishStrings : AppStrings {
    override val appName = "AI SMS Manager"
    override val tabMessages = "Messages"
    override val tabSearch = "Search"
    override val tabNotifications = "Alerts"
    override val tabSettings = "Settings"
    override val defaultSmsBannerTitle = "Set as Default SMS App"
    override val defaultSmsBannerBody = "Enable sending, receiving, and live delivery reports."
    override val setAsDefaultSms = "Set Default"
    override val smsPermissionsMissingTitle = "SMS & Contacts Access Needed"
    override val smsPermissionsMissingBody = "Grant Read & Send SMS permissions to load your threads and send replies."
    override val grantPermissions = "Grant Access"
    override val smsRationaleTitle = "Why SMS Permissions Are Needed"
    override val smsRationaleBody = "AI SMS Manager needs SMS and Contacts permissions to work as your messaging & archive hub:"
    override val smsRationaleReadPoint = "• Read & Receive SMS: Syncs your inbox into the local searchable database."
    override val smsRationaleSendPoint = "• Send SMS: Lets you compose and reply to messages directly with delivery reports."
    override val smsRationaleContactsPoint = "• Contacts: Displays sender names instead of raw phone numbers."
    override val smsPermissionDeniedTitle = "SMS Permission Required"
    override val smsPermissionDeniedBody = "Read or Send SMS access is turned off in Android settings. Open App Settings to enable SMS permissions."
    override val openAppPermissions = "Open App Settings"
    override val continueAction = "Continue"
    override val sendSmsPermissionBanner = "Send SMS permission is required to send messages."
    override val emptyThreadHint = "No messages in this conversation yet. Write a message below to send an SMS."
    override val syncSmsNow = "Sync SMS"
    override val syncing = "Syncing..."
    override val newMessage = "New SMS"
    override val noConversationsTitle = "No SMS conversations yet"
    override val noConversationsBody = "Tap New SMS to start messaging or grant SMS permission to load your inbox."
    override val typeMessageHint = "Write an SMS..."
    override val send = "Send"
    override val unknownSender = "Unknown"
    override val statusSending = "Sending"
    override val statusSent = "Sent"
    override val statusDelivered = "Delivered"
    override val statusFailed = "Failed"
    override val incoming = "Incoming"
    override val outgoing = "Outgoing"
    override val saveContact = "Save Contact"
    override val viewContact = "Contact"
    override val deleteMessage = "Delete message"
    override val deleteThread = "Delete thread"
    override val selectContactTitle = "New Conversation"
    override val searchContactsOrNumberHint = "Search contacts or type phone number..."
    override val startChatWithNumber = "Message"
    override val noContactsFound = "No matching contacts found"
    override val searchModeLocal = "Local Search"
    override val searchModeAiTopics = "AI Chat Topics"
    override val searchPlaceholder = "Search full text of all SMS & notifications..."
    override val sortNewest = "Newest"
    override val sortOldest = "Oldest"
    override val sortSenderAsc = "Sender A–Z"
    override val filterAll = "All"
    override val filterSmsOnly = "SMS"
    override val filterNotificationsOnly = "Notifications"
    override val filterIncoming = "Incoming"
    override val filterOutgoing = "Outgoing"
    override val filterDateRange = "Date Range"
    override val clearDateFilter = "Clear Dates"
    override val resultsCountLabel = "results"
    override val noSearchResultsTitle = "No matching records"
    override val noSearchResultsBody = "Try another keyword or relax your source and date filters."
    override val tapToOpenInThread = "Open in sender thread"
    override val smsLabel = "SMS"
    override val notificationLabel = "Notification"
    override val aiTopicsTitle = "AI Search Topics"
    override val newTopic = "New Topic"
    override val deleteTopic = "Delete Topic"
    override val noTopicsTitle = "Start an isolated AI search topic"
    override val noTopicsBody = "Each topic keeps its own XML archive scope and chat history so questions never mix."
    override val askAiHint = "Ask AI about your messages & notifications..."
    override val selectHistoryPromptTitle = "Choose Archive History for AI"
    override val selectHistoryPromptSubtitle = "Select how far back to convert SMS & notifications into XML before sending."
    override val presetToday = "Today"
    override val presetTwoDays = "Last 2 Days"
    override val presetThreeDays = "Last 3 Days"
    override val presetOneWeek = "Last 1 Week"
    override val presetTwoWeeks = "Last 2 Weeks"
    override val presetOneMonth = "Last 1 Month"
    override val presetOneYear = "Last 1 Year"
    override val presetCustomRange = "Custom Range"
    override val customStartDate = "Start Date"
    override val customEndDate = "End Date"
    override val maxItemsLabel = "Max items in XML"
    override val includeSmsLabel = "Include SMS"
    override val includeNotificationsLabel = "Include Notifications"
    override val matchedArchiveSummary = "Archive ready"
    override val previewXml = "Preview XML"
    override val sendToAiWithXml = "Attach XML & Ask"
    override val xmlPreviewTitle = "Generated XML Archive"
    override val copyXml = "Copy XML"
    override val copiedToClipboard = "Copied to clipboard"
    override val aiNotConfiguredTitle = "Configure AI API First"
    override val aiNotConfiguredBody = "Set your API endpoint, model name, and API key in Settings to use AI search."
    override val openSettings = "Open Settings"
    override val aiThinking = "Analyzing XML archive..."
    override val matchedReferencesTitle = "Matched Messages & Alerts"
    override val changeScope = "Change Range"
    override val notificationsTitle = "Notification Archive"
    override val notifAccessDisabledTitle = "Notification Access Disabled"
    override val notifAccessDisabledBody = "Allow Notification Access in Android Settings to archive notifications in real time."
    override val enableNotifAccess = "Open Access Settings"
    override val notifCapturePausedTitle = "Archiving Paused in Settings"
    override val notifCapturePausedBody = "Turn on Notification Archiving in Settings to capture incoming notifications."
    override val clearAllNotifications = "Clear Archive"
    override val filterAppsHint = "Filter by app, title, or content..."
    override val noNotificationsTitle = "No archived notifications yet"
    override val noNotificationsBody = "Incoming notifications will appear here automatically once enabled."
    override val settingsTitle = "Settings"
    override val sectionLanguage = "Language & Calendar"
    override val languageSystem = "System Default"
    override val languageEnglish = "English (Gregorian)"
    override val languagePersian = "Persian / فارسی (Jalali)"
    override val calendarModeLabel = "Calendar Mode"
    override val calendarAuto = "Auto (Follow Language)"
    override val calendarJalali = "Jalali (Persian)"
    override val calendarGregorian = "Gregorian"
    override val searchMessagesHint = "Search SMS messages (FTS)..."
    override val closeSearch = "Close search"
    override val sectionMessagingAndArchive = "SMS & Notification Archive"
    override val defaultSmsStatusActive = "Active Default SMS App"
    override val defaultSmsStatusInactive = "Not Default SMS App (Tap to set)"
    override val captureNotificationsToggle = "Real-time Notification Archive"
    override val captureNotificationsSubtitle = "Save incoming notifications locally for search & AI XML export"
    override val systemNotificationAccess = "Android Notification Access"
    override val sectionAiConfiguration = "AI Search API Configuration"
    override val aiProviderLabel = "API Format"
    override val providerOpenAiCompat = "OpenAI Compatible"
    override val providerGeminiRest = "Gemini REST"
    override val providerCustom = "Custom OpenAI-Style"
    override val aiBaseUrlLabel = "Endpoint / Base URL"
    override val aiModelLabel = "Model ID"
    override val aiApiKeyLabel = "API Key (Encrypted on device)"
    override val saveAiConfig = "Save API Settings"
    override val testAiConnection = "Test Connection"
    override val aiConfigSaved = "AI settings saved securely"
    override val aiSetupHint = "For OpenAI-compatible gateways (e.g. .../v1/), select OpenAI Compatible, enter Base URL & API Key, then tap Fetch Models or Test Connection."
    override val smartPasteConfig = "Paste URL & Key from Clipboard"
    override val smartPasteEmpty = "No URL or API key detected in clipboard"
    override val smartPasteApplied = "Detected URL / API key filled below"
    override val fetchModels = "Fetch Models"
    override val fetchedModelsLabel = "Available models (tap to select):"
    override val clearAiConfig = "Clear"
    override val aiConfigCleared = "API key and custom endpoint cleared"
    override val resolvedEndpointLabel = "Chat endpoint"
    override val aiContextModeLabel = "AI Search Data Mode"
    override val contextCompactText = "Smart Text (No Upload)"
    override val contextKeywordPrefilter = "FTS Keyword Digest"
    override val contextInlineXml = "Inline XML Text"
    override val contextNoUploadHint = "Works with any LLM without requiring file upload support."
    override val aiSummaryLabel = "Summary"
    override val aiMatchReasonPrefix = "Why it matched"
    override val suggestedFollowUpsLabel = "Follow-up questions"
    override val sectionGoogleDriveBackup = "Google Drive Backup & Restore"
    override val driveConnectButton = "Connect Google Drive"
    override val driveDisconnectButton = "Disconnect"
    override val driveConnectedLabel = "Connected"
    override val driveNotConnectedHint = "Connect your Google account to back up and restore selected SMS & notifications on Google Drive."
    override val backupScopeLabel = "Select items for Backup & Restore:"
    override val backupNowButton = "Back Up to Drive"
    override val refreshDriveBackups = "Refresh"
    override val restoreButton = "Restore"
    override val exportLocalBackup = "Export JSON"
    override val importLocalBackup = "Import JSON"
    override val driveBackupsEmpty = "No Google Drive backups found yet."
    override val lastBackupLabel = "Last backup"
    override val backupSyncModeLabel = "Backup Schedule Mode"
    override val backupModeManual = "Manual"
    override val backupModeAutomatic = "Automatic"
    override val backupModeAutoSubtitle = "Automatically backs up selected logs daily to Google Drive when online"
    override val backupModeManualSubtitle = "Manual backup only (tap Back Up to Drive anytime)"
    override val tabAiChat = "AI Chat"
    override val aiChatTitle = "AI Assistant Chat"
    override val sourceSmsThread = "SMS Thread"
    override val sourceNotificationHistory = "Notifications"
    override val sourceCombinedArchive = "All Archive"
    override val notificationHistoryLabel = "Notification History"
    override val selectSmsThreadTitle = "Select SMS Thread"
    override val selectSmsThreadHint = "Choose a conversation to include in your XML prompt"
    override val noThreadSelected = "Tap to choose an SMS thread"
    override val allAppsFilter = "All Apps"
    override val autoXmlPromptBadge = "Auto XML Prompt"
    override val askAiAboutThread = "Ask AI about this thread"
    override val askAiAboutNotifications = "Ask AI about notifications"
    override val quickSuggestSummarize = "Summarize this conversation"
    override val quickSuggestOtpAmounts = "Find OTPs & payment amounts"
    override val quickSuggestDates = "Extract dates & key actions"
    override val quickSuggestNotifSummary = "Summarize recent notifications"
    override val quickSuggestNotifFinance = "Any bank alerts or deliveries?"
    override val aiChatEmptyTitle = "Chat with your SMS & Alert History"
    override val aiChatEmptyBody = "Pick an SMS thread or notification history above. Every question is automatically formatted as a structured XML prompt."
    override val newChat = "New Chat"
    override val openAiChatScreen = "Open Dedicated AI Chat"
    override val sectionAppearance = "Appearance"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val dynamicColorToggle = "Material You Dynamic Color"
    override val dynamicColorSubtitle = "Adapt colors to device wallpaper on Android 12+"
    override val sectionAbout = "App Release Info"
    override val versionLabel = "Version"
    override val cancel = "Cancel"
    override val apply = "Apply"
    override val confirm = "Confirm"
    override val back = "Back"
    override val yearLabel = "Year"
    override val monthLabel = "Month"
    override val dayLabel = "Day"
    override val copyMessage = "Copy"
    override val copyLink = "Copy Link"
    override val linksLabel = "Links"
    override val openLink = "Open Link"
    override val openNotificationApp = "Open Alert"
    override val pinThread = "Pin Chat"
    override val unpinThread = "Unpin Chat"
    override val pinnedThreadsBadge = "Pinned"
    override val pinMessage = "Pin Message"
    override val unpinMessage = "Unpin"
    override val pinnedMessagesBanner = "Pinned Messages"
    override val viewAllPinned = "View All"
    override val noPinnedMessages = "No pinned messages in this conversation."
    override val jumpToMessage = "Jump"
    override val dismissForToday = "Hide today"
    override val aiContextLimitLabel = "Model Context Limit (Tokens)"
    override val aiCapabilitiesLabel = "Detected Model Capabilities"
    override val capabilityFileSupported = "File Upload: Supported"
    override val capabilityFileUnsupported = "File Upload: Not Supported"
    override val capabilityImageSupported = "Image Input: Supported"
    override val capabilityImageUnsupported = "Image Input: Not Supported"
    override val capabilityDetectedCtx = "Detected Model Window"
    override val contextSmartAuto = "Smart Auto (Context + Upload)"
    override val contextSmartAutoDesc = "Uploads full file if model supports it; otherwise sends the last N messages fitting your context limit."
    override val smartFileUploadedBadge = "Full Archive File Upload"
    override val smartBudgetLastNBadge = "Smart Last-N Budget"
    override val sectionNotificationSettings = "Notification Display & Ringtone"
    override val incomingSmsNotifToggle = "Incoming SMS Notifications"
    override val incomingSmsNotifSubtitle = "Show alerts when new SMS messages arrive"
    override val notifPreviewTextToggle = "Show Message Content Preview"
    override val notifPreviewTextSubtitle = "Hide sensitive message text in alerts when turned off"
    override val notifVibrateToggle = "Vibrate on Incoming SMS"
    override val smsRingtoneLabel = "SMS Notification Sound"
    override val openSystemChannelSettings = "System Notification Sound & Channel Settings"
    override val ringtoneDefault = "System Default Sound"
    override val ringtoneSilent = "Silent"
    override val driveTroubleshootingTitle = "Sign-In Helper & Diagnostics"
    override val driveFallbackAccountButton = "Sign in with Google Account (Fallback)"
    override val driveSha1Label = "APK Signing SHA-1"
    override val drivePackageLabel = "Package Name"
    override val driveCopyDiagInfo = "Copy Package & SHA-1"
    override val driveCustomClientIdLabel = "OAuth Client ID (Android/Installed, Optional)"
    override val driveBrowserLoginButton = "Sign in via Browser (PKCE)"
    override val drivePasteTokenLabel = "Or paste Google Drive OAuth Access Token"
    override val driveConnectWithTokenButton = "Connect Token"
    override val driveDiagnosticsHint = "If Google Play Sign-In returns error 10/12500 on this device, register the SHA-1 below in Google Cloud Console, use the Google Account fallback, or connect via Browser PKCE."
    override val tabArchive = "Archive"
    override val archiveTitle = "Searches & AI Archive"
    override val archiveFilterAll = "All"
    override val archiveFilterSearches = "Searches"
    override val archiveFilterAiChats = "AI Chats"
    override val archiveSearchHint = "Filter past searches, AI answers, or snippets..."
    override val saveSearchAction = "Save Search"
    override val searchSavedToast = "Search saved to archive"
    override val recentSearchesLabel = "Recent Searches"
    override val savedAiChatsLabel = "AI Chat Archive"
    override val noArchiveItemsTitle = "No archived history yet"
    override val noArchiveItemsBody = "Performed searches and AI conversations are saved here so you can reopen previous results anytime."
    override val clearSearchHistory = "Clear Searches"
    override val rerunSearch = "Re-run Search"
    override val continueAiChat = "Open in AI Chat"
    override val archivedResultsCountLabel = "saved results"
    override val messagesCountLabel = "messages"
    override val matchedItemsBadge = "matched"
    override val originSearchTab = "Search"
    override val originSmsBar = "SMS Bar"
    override val originAiChatTab = "AI Chat"
    override val originAiTopicsTab = "AI Topic"
    override val archivedSnapshotNote = "Viewing archived results snapshot"
    override val threadAiHistoryBanner = "Previous AI Chats for Thread"
    override val noThreadAiHistory = "No previous AI chats recorded for this thread yet."
    override val markAllAsRead = "Mark all as read"
    override val allMarkedAsRead = "All messages marked as read"
    override val openArchiveButton = "Archive"
    override val exportConversation = "Export Conversation"
    override val exportAsText = "Export Readable Text (.txt)"
    override val exportAsJson = "Export JSON (.json)"
    override val exportCompletedToast = "Conversation exported successfully"
    override val aiMaxOutputTokensLabel = "Max Output Tokens"
    override val capabilitySystemSupported = "System Prompt: Supported"
    override val capabilitySystemUnsupported = "System Prompt: Folded to User"
    override val capabilityTempSupported = "Temperature: Supported"
    override val capabilityTempUnsupported = "Temperature: Fixed/Omitted"
    override val capabilityJsonSupported = "JSON Output: Supported"
    override val aiConstraintsAppliedNote = "Model constraints saved and dynamically enforced on future requests."
    override val driveLoopbackPkceButton = "Sign in via Browser (Loopback PKCE)"
    override val driveLoopbackHint = "No SHA-1 needed when using a Desktop OAuth Client ID (redirects automatically via 127.0.0.1)."
}

private object PersianStrings : AppStrings {
    override val appName = "مدیریت پیامک هوشمند"
    override val tabMessages = "پیامک‌ها"
    override val tabSearch = "جستجو"
    override val tabNotifications = "اعلان‌ها"
    override val tabSettings = "تنظیمات"
    override val defaultSmsBannerTitle = "تنظیم به عنوان برنامه پیش‌فرض پیامک"
    override val defaultSmsBannerBody = "برای ارسال، دریافت و گزارش تحویل لحظه‌ای برنامه را پیش‌فرض کنید."
    override val setAsDefaultSms = "پیش‌فرض کن"
    override val smsPermissionsMissingTitle = "نیاز به دسترسی خواندن و ارسال پیامک"
    override val smsPermissionsMissingBody = "برای نمایش گفتگوها و ارسال پاسخ، دسترسی پیامک و مخاطبین را تایید کنید."
    override val grantPermissions = "اعطای دسترسی"
    override val smsRationaleTitle = "چرا به دسترسی پیامک نیاز داریم؟"
    override val smsRationaleBody = "مدیریت پیامک هوشمند برای عملکرد کامل به عنوان برنامه پیامک و آرشیو جستجو به این دسترسی‌ها نیاز دارد:"
    override val smsRationaleReadPoint = "• خواندن و دریافت پیامک: همگام‌سازی پیامک‌ها در پایگاه داده محلی برای جستجوی فوری."
    override val smsRationaleSendPoint = "• ارسال پیامک: امکان ارسال مستقیم پیامک و دریافت گزارش تحویل."
    override val smsRationaleContactsPoint = "• مخاطبین: نمایش نام فرستندگان به جای شماره تلفن خام."
    override val smsPermissionDeniedTitle = "دسترسی پیامک مسدود شده است"
    override val smsPermissionDeniedBody = "دسترسی خواندن یا ارسال پیامک در تنظیمات اندروید غیرفعال شده است. برای فعال‌سازی وارد تنظیمات برنامه شوید."
    override val openAppPermissions = "تنظیمات برنامه"
    override val continueAction = "ادامه و تایید"
    override val sendSmsPermissionBanner = "برای ارسال پیامک، تایید دسترسی Send SMS الزامی است."
    override val emptyThreadHint = "هنوز پیامی در این گفتگو وجود ندارد. برای ارسال پیامک متن خود را در پایین بنویسید."
    override val syncSmsNow = "همگام‌سازی"
    override val syncing = "در حال همگام‌سازی..."
    override val newMessage = "پیام جدید"
    override val noConversationsTitle = "هنوز گفتگویی ثبت نشده است"
    override val noConversationsBody = "برای ارسال پیام جدید دکمه پیام جدید را بزنید یا دسترسی پیامک را فعال کنید."
    override val typeMessageHint = "متن پیامک را بنویسید..."
    override val send = "ارسال"
    override val unknownSender = "ناشناس"
    override val statusSending = "در حال ارسال"
    override val statusSent = "ارسال شد"
    override val statusDelivered = "تحویل داده شد"
    override val statusFailed = "ناموفق"
    override val incoming = "دریافتی"
    override val outgoing = "ارسالی"
    override val saveContact = "ذخیره مخاطب"
    override val viewContact = "مخاطب"
    override val deleteMessage = "حذف پیام"
    override val deleteThread = "حذف گفتگو"
    override val selectContactTitle = "گفتگوی جدید"
    override val searchContactsOrNumberHint = "جستجوی مخاطب یا شماره تلفن..."
    override val startChatWithNumber = "ارسال پیام به"
    override val noContactsFound = "مخاطبی پیدا نشد"
    override val searchModeLocal = "جستجوی فوری"
    override val searchModeAiTopics = "چت هوش مصنوعی"
    override val searchPlaceholder = "جستجو در متن کامل همه پیامک‌ها و نوتیفیکیشن‌ها..."
    override val sortNewest = "جدید به قدیم"
    override val sortOldest = "قدیم به جدید"
    override val sortSenderAsc = "فرستنده (الف-ی)"
    override val filterAll = "همه"
    override val filterSmsOnly = "فقط پیامک"
    override val filterNotificationsOnly = "فقط اعلان"
    override val filterIncoming = "دریافتی"
    override val filterOutgoing = "ارسالی"
    override val filterDateRange = "بازه تاریخی"
    override val clearDateFilter = "حذف تاریخ"
    override val resultsCountLabel = "نتیجه"
    override val noSearchResultsTitle = "موردی پیدا نشد"
    override val noSearchResultsBody = "عبارت دیگری جستجو کنید یا فیلتر منبع و تاریخ را تغییر دهید."
    override val tapToOpenInThread = "مشاهده در گفتگوی فرستنده"
    override val smsLabel = "پیامک"
    override val notificationLabel = "اعلان"
    override val aiTopicsTitle = "تاپیک‌های جستجوی هوشمند"
    override val newTopic = "تاپیک جدید"
    override val deleteTopic = "حذف تاپیک"
    override val noTopicsTitle = "یک تاپیک مجزا برای جستجو بسازید"
    override val noTopicsBody = "هر تاپیک تاریخچه چت و فایل XML مخصوص خود را دارد تا سوالات با هم تداخل نکنند."
    override val askAiHint = "سوال خود را درباره پیام‌ها و اعلان‌ها بپرسید..."
    override val selectHistoryPromptTitle = "انتخاب بازه زمانی آرشیو برای هوش مصنوعی"
    override val selectHistoryPromptSubtitle = "مشخص کنید تاریخچه پیامک‌ها و اعلان‌های چند وقت گذشته به XML تبدیل و ارسال شود."
    override val presetToday = "امروز"
    override val presetTwoDays = "۲ روز گذشته"
    override val presetThreeDays = "۳ روز گذشته"
    override val presetOneWeek = "یک هفته گذشته"
    override val presetTwoWeeks = "۲ هفته گذشته"
    override val presetOneMonth = "یک ماه گذشته"
    override val presetOneYear = "یک سال گذشته"
    override val presetCustomRange = "بازه تاریخی دلخواه"
    override val customStartDate = "تاریخ شروع"
    override val customEndDate = "تاریخ پایان"
    override val maxItemsLabel = "حداکثر تعداد پیام در XML"
    override val includeSmsLabel = "شامل پیامک‌ها"
    override val includeNotificationsLabel = "شامل نوتیفیکیشن‌ها"
    override val matchedArchiveSummary = "آرشیو آماده ارسال"
    override val previewXml = "پیش‌نمایش XML"
    override val sendToAiWithXml = "ارسال XML و پرسش"
    override val xmlPreviewTitle = "فایل XML تولید شده"
    override val copyXml = "کپی XML"
    override val copiedToClipboard = "در حافظه کپی شد"
    override val aiNotConfiguredTitle = "ابتدا API هوش مصنوعی را تنظیم کنید"
    override val aiNotConfiguredBody = "برای جستجو با هوش مصنوعی، آدرس API، نام مدل و کلید را در تنظیمات وارد کنید."
    override val openSettings = "رفتن به تنظیمات"
    override val aiThinking = "در حال تحلیل آرشیو XML..."
    override val matchedReferencesTitle = "پیام‌ها و اعلان‌های مرتبط"
    override val changeScope = "تغییر بازه"
    override val notificationsTitle = "آرشیو نوتیفیکیشن‌ها"
    override val notifAccessDisabledTitle = "دسترسی نوتیفیکیشن غیرفعال است"
    override val notifAccessDisabledBody = "برای ذخیره لحظه‌ای اعلان‌ها، دسترسی Notification Access را در تنظیمات اندروید فعال کنید."
    override val enableNotifAccess = "تنظیم دسترسی اندروید"
    override val notifCapturePausedTitle = "ذخیره اعلان در تنظیمات خاموش است"
    override val notifCapturePausedBody = "برای ثبت اعلان‌های جدید، گزینه آرشیو نوتیفیکیشن را در تنظیمات روشن کنید."
    override val clearAllNotifications = "پاکسازی آرشیو"
    override val filterAppsHint = "فیلتر بر اساس برنامه، عنوان یا متن..."
    override val noNotificationsTitle = "هنوز اعلانی آرشیو نشده است"
    override val noNotificationsBody = "پس از فعال‌سازی، اعلان‌های دریافتی به صورت لحظه‌ای اینجا ذخیره می‌شوند."
    override val settingsTitle = "تنظیمات"
    override val sectionLanguage = "زبان و تقویم"
    override val languageSystem = "مطابق زبان گوشی"
    override val languageEnglish = "انگلیسی (تقویم میلادی)"
    override val languagePersian = "فارسی (تقویم شمسی)"
    override val calendarModeLabel = "نوع تقویم"
    override val calendarAuto = "خودکار (مطابق زبان)"
    override val calendarJalali = "شمسی (جلالی)"
    override val calendarGregorian = "میلادی"
    override val searchMessagesHint = "جستجوی لحظه‌ای در متن پیامک‌ها..."
    override val closeSearch = "بستن جستجو"
    override val sectionMessagingAndArchive = "پیامک و آرشیو نوتیفیکیشن"
    override val defaultSmsStatusActive = "برنامه پیش‌فرض پیامک فعال است"
    override val defaultSmsStatusInactive = "پیش‌فرض نیست (برای تنظیم لمس کنید)"
    override val captureNotificationsToggle = "آرشیو لحظه‌ای نوتیفیکیشن‌ها"
    override val captureNotificationsSubtitle = "ذخیره محلی اعلان‌ها برای جستجو و ارسال XML به هوش مصنوعی"
    override val systemNotificationAccess = "دسترسی خواندن اعلان‌های سیستم"
    override val sectionAiConfiguration = "تنظیمات API هوش مصنوعی"
    override val aiProviderLabel = "فرمت API"
    override val providerOpenAiCompat = "سازگار با OpenAI"
    override val providerGeminiRest = "Gemini REST"
    override val providerCustom = "آدرس سفارشی"
    override val aiBaseUrlLabel = "آدرس Endpoint / Base URL"
    override val aiModelLabel = "نام مدل (Model ID)"
    override val aiApiKeyLabel = "کلید API (ذخیره رمزنگاری‌شده)"
    override val saveAiConfig = "ذخیره تنظیمات API"
    override val testAiConnection = "تست اتصال"
    override val aiConfigSaved = "تنظیمات هوش مصنوعی با امنیت ذخیره شد"
    override val aiSetupHint = "برای سرویس‌های سازگار با OpenAI (دارای /v1/)، گزینه «سازگار با OpenAI» را انتخاب کنید، آدرس Base URL و کلید را وارد کرده و «دریافت مدل‌ها» یا «تست اتصال» را بزنید."
    override val smartPasteConfig = "جای‌گذاری هوشمند آدرس و کلید از کلیپ‌بورد"
    override val smartPasteEmpty = "آدرس یا کلید معتبری در کلیپ‌بورد پیدا نشد"
    override val smartPasteApplied = "آدرس و کلید شناسایی‌شده در فیلدها قرار گرفت"
    override val fetchModels = "دریافت مدل‌ها"
    override val fetchedModelsLabel = "مدل‌های در دسترس (برای انتخاب لمس کنید):"
    override val clearAiConfig = "پاکسازی"
    override val aiConfigCleared = "کلید API و تنظیمات سفارشی پاک شد"
    override val resolvedEndpointLabel = "آدرس نهایی چت"
    override val aiContextModeLabel = "روش ارسال به هوش مصنوعی"
    override val contextCompactText = "متن هوشمند (بدون آپلود)"
    override val contextKeywordPrefilter = "خلاصه فیلتر کلمات (FTS)"
    override val contextInlineXml = "متن XML داخل پرامپت"
    override val contextNoUploadHint = "بدون نیاز به آپلود فایل XML و سازگار با همه مدل‌های زبانی."
    override val aiSummaryLabel = "خلاصه"
    override val aiMatchReasonPrefix = "دلیل تطابق"
    override val suggestedFollowUpsLabel = "سوالات پیشنهادی"
    override val sectionGoogleDriveBackup = "بکاپ و بازگردانی Google Drive"
    override val driveConnectButton = "اتصال به Google Drive"
    override val driveDisconnectButton = "قطع اتصال"
    override val driveConnectedLabel = "متصل"
    override val driveNotConnectedHint = "برای تهیه نسخه پشتیبان و بازگردانی پیامک‌ها یا اعلان‌های انتخابی به Google Drive متصل شوید."
    override val backupScopeLabel = "انتخاب موارد برای بکاپ و بازگردانی:"
    override val backupNowButton = "بکاپ در Google Drive"
    override val refreshDriveBackups = "به‌روزرسانی"
    override val restoreButton = "بازگردانی"
    override val exportLocalBackup = "خروجی JSON"
    override val importLocalBackup = "ورود از JSON"
    override val driveBackupsEmpty = "هنوز بکاپی در Google Drive پیدا نشد."
    override val lastBackupLabel = "آخرین بکاپ"
    override val backupSyncModeLabel = "نحوه پشتیبان‌گیری"
    override val backupModeManual = "دستی"
    override val backupModeAutomatic = "خودکار"
    override val backupModeAutoSubtitle = "بکاپ خودکار روزانه از موارد انتخابی هنگام اتصال به اینترنت"
    override val backupModeManualSubtitle = "فقط بکاپ دستی (هر زمان بخواهید دکمه بکاپ را بزنید)"
    override val tabAiChat = "چت AI"
    override val aiChatTitle = "گفتگو با هوش مصنوعی"
    override val sourceSmsThread = "گفتگوی پیامکی"
    override val sourceNotificationHistory = "تاریخچه اعلان‌ها"
    override val sourceCombinedArchive = "کل آرشیو"
    override val notificationHistoryLabel = "تاریخچه نوتیفیکیشن‌ها"
    override val selectSmsThreadTitle = "انتخاب گفتگوی پیامکی"
    override val selectSmsThreadHint = "یک گفتگو را برای تبدیل خودکار به پرامپت XML انتخاب کنید"
    override val noThreadSelected = "برای انتخاب گفتگو لمس کنید"
    override val allAppsFilter = "همه برنامه‌ها"
    override val autoXmlPromptBadge = "پرامپت خودکار XML"
    override val askAiAboutThread = "پرسش از AI درباره این گفتگو"
    override val askAiAboutNotifications = "پرسش از AI درباره اعلان‌ها"
    override val quickSuggestSummarize = "این گفتگو را خلاصه کن"
    override val quickSuggestOtpAmounts = "کدهای تایید و مبالغ را پیدا کن"
    override val quickSuggestDates = "تاریخ‌ها و کارهای مهم را استخراج کن"
    override val quickSuggestNotifSummary = "اعلان‌های اخیر را خلاصه کن"
    override val quickSuggestNotifFinance = "تراکنش بانکی یا سفارش ارسالی هست؟"
    override val aiChatEmptyTitle = "گفتگو با آرشیو پیامک و اعلان‌ها"
    override val aiChatEmptyBody = "در بالا یک گفتگوی پیامکی یا تاریخچه اعلان‌ها را انتخاب کنید؛ سوال شما به صورت خودکار در قالب XML ساختاریافته ارسال می‌شود."
    override val newChat = "گفتگوی جدید"
    override val openAiChatScreen = "ورود به صفحه اختصاصی چت AI"
    override val sectionAppearance = "ظاهر برنامه"
    override val themeSystem = "مطابق سیستم"
    override val themeLight = "روشن"
    override val themeDark = "تیره"
    override val dynamicColorToggle = "رنگ‌بندی پویای متریال ۳"
    override val dynamicColorSubtitle = "هماهنگی رنگ‌ها با پس‌زمینه در اندروید ۱۲ به بالا"
    override val sectionAbout = "اطلاعات نسخه"
    override val versionLabel = "نسخه"
    override val cancel = "انصراف"
    override val apply = "اعمال"
    override val confirm = "تایید"
    override val back = "بازگشت"
    override val yearLabel = "سال"
    override val monthLabel = "ماه"
    override val dayLabel = "روز"
    override val copyMessage = "کپی متن"
    override val copyLink = "کپی لینک"
    override val linksLabel = "لینک‌ها"
    override val openLink = "باز کردن لینک"
    override val openNotificationApp = "باز کردن اعلان"
    override val pinThread = "پین گفتگو"
    override val unpinThread = "برداشتن پین"
    override val pinnedThreadsBadge = "پین‌شده"
    override val pinMessage = "پین پیام"
    override val unpinMessage = "برداشتن پین"
    override val pinnedMessagesBanner = "پیام‌های پین‌شده"
    override val viewAllPinned = "مشاهده همه"
    override val noPinnedMessages = "پیام پین‌شده‌ای در این گفتگو وجود ندارد."
    override val jumpToMessage = "رفتن به پیام"
    override val dismissForToday = "عدم نمایش تا فردا"
    override val aiContextLimitLabel = "محدودیت کانتکس مدل (تعداد توکن)"
    override val aiCapabilitiesLabel = "قابلیت‌های شناسایی‌شده مدل"
    override val capabilityFileSupported = "آپلود فایل: پشتیبانی می‌شود"
    override val capabilityFileUnsupported = "آپلود فایل: پشتیبانی نمی‌شود"
    override val capabilityImageSupported = "ورودی تصویر: پشتیبانی می‌شود"
    override val capabilityImageUnsupported = "ورودی تصویر: پشتیبانی نمی‌شود"
    override val capabilityDetectedCtx = "کانتکس شناسایی‌شده مدل"
    override val contextSmartAuto = "حالت هوشمند (کانتکس + آپلود)"
    override val contextSmartAutoDesc = "در صورت پشتیبانی مدل، کل آرشیو را به صورت فایل ارسال می‌کند؛ در غیر این صورت تنها N پیام آخر متناسب با کانتکس ارسال می‌شود."
    override val smartFileUploadedBadge = "ارسال فایل کامل آرشیو"
    override val smartBudgetLastNBadge = "ارسال هوشمند N پیام آخر"
    override val sectionNotificationSettings = "تنظیمات نمایش نوتیفیکیشن و رینگتون"
    override val incomingSmsNotifToggle = "نمایش نوتیفیکیشن پیامک ورودی"
    override val incomingSmsNotifSubtitle = "نمایش اعلان هنگام دریافت پیامک جدید"
    override val notifPreviewTextToggle = "نمایش متن پیام در نوتیفیکیشن"
    override val notifPreviewTextSubtitle = "در صورت خاموش بودن، متن پیامک برای حفظ حریم خصوصی مخفی می‌شود"
    override val notifVibrateToggle = "لرزش هنگام دریافت پیامک"
    override val smsRingtoneLabel = "صدای زنگ (رینگتون) پیامک"
    override val openSystemChannelSettings = "تنظیمات کانال و صدای سیستم اندروید"
    override val ringtoneDefault = "صدای پیش‌فرض سیستم"
    override val ringtoneSilent = "بی‌صدا"
    override val driveTroubleshootingTitle = "راهنمای رفع خطای ورود و روش‌های جایگزین"
    override val driveFallbackAccountButton = "ورود با حساب گوگل گوشی (روش جایگزین)"
    override val driveSha1Label = "امضای SHA-1 برنامه"
    override val drivePackageLabel = "شناسه بسته (Package)"
    override val driveCopyDiagInfo = "کپی شناسه و SHA-1"
    override val driveCustomClientIdLabel = "شناسه OAuth Client ID (اختیاری برای مرورگر)"
    override val driveBrowserLoginButton = "ورود از طریق مرورگر (PKCE)"
    override val drivePasteTokenLabel = "یا جای‌گذاری مستقیم Access Token گوگل درایو"
    override val driveConnectWithTokenButton = "اتصال با توکن"
    override val driveDiagnosticsHint = "اگر اتصال مستقیم گوگل روی گوشی با خطای 10 یا 12500 مواجه شد، از دکمه «ورود با حساب گوگل گوشی» استفاده کنید یا SHA-1 زیر را در Google Cloud ثبت نمایید."
    override val tabArchive = "آرشیو"
    override val archiveTitle = "آرشیو جستجوها و چت AI"
    override val archiveFilterAll = "همه"
    override val archiveFilterSearches = "جستجوها"
    override val archiveFilterAiChats = "چت‌های AI"
    override val archiveSearchHint = "جستجو در سابقه جستجوها، پاسخ‌های AI یا نتایج..."
    override val saveSearchAction = "ذخیره جستجو"
    override val searchSavedToast = "جستجو در آرشیو ذخیره شد"
    override val recentSearchesLabel = "جستجوهای اخیر"
    override val savedAiChatsLabel = "آرشیو چت‌های AI"
    override val noArchiveItemsTitle = "هنوز موردی در آرشیو ثبت نشده است"
    override val noArchiveItemsBody = "جستجوهای انجام‌شده و گفتگوهای هوش مصنوعی اینجا ذخیره می‌شوند تا هر زمان به نتایج قبلی دسترسی داشته باشید."
    override val clearSearchHistory = "پاکسازی جستجوها"
    override val rerunSearch = "اجرای مجدد"
    override val continueAiChat = "باز کردن در چت AI"
    override val archivedResultsCountLabel = "نتیجه ذخیره‌شده"
    override val messagesCountLabel = "پیام"
    override val matchedItemsBadge = "مورد مرتبط"
    override val originSearchTab = "جستجو"
    override val originSmsBar = "جستجوی پیامک"
    override val originAiChatTab = "چت AI"
    override val originAiTopicsTab = "تاپیک AI"
    override val archivedSnapshotNote = "نمایش نتایج ذخیره‌شده از آرشیو"
    override val threadAiHistoryBanner = "سابقه گفتگوهای AI این رشته پیام"
    override val noThreadAiHistory = "هنوز گفتگوی AI برای این رشته پیامک ثبت نشده است."
    override val markAllAsRead = "علامت‌گذاری همه به عنوان خوانده‌شده"
    override val allMarkedAsRead = "تمام پیام‌ها به عنوان خوانده‌شده علامت‌گذاری شدند"
    override val openArchiveButton = "آرشیو"
    override val exportConversation = "خروجی گفتگو (ذخیره محلی)"
    override val exportAsText = "خروجی متنی خوانا (.txt)"
    override val exportAsJson = "خروجی ساخت‌یافته (.json)"
    override val exportCompletedToast = "فایل گفتگو با موفقیت ذخیره شد"
    override val aiMaxOutputTokensLabel = "حداکثر توکن خروجی"
    override val capabilitySystemSupported = "System Prompt: پشتیبانی می‌شود"
    override val capabilitySystemUnsupported = "System Prompt: ادغام در پیام کاربر"
    override val capabilityTempSupported = "Temperature: پشتیبانی می‌شود"
    override val capabilityTempUnsupported = "Temperature: حذف خودکار"
    override val capabilityJsonSupported = "خروجی JSON: پشتیبانی می‌شود"
    override val aiConstraintsAppliedNote = "محدودیت‌های مدل در حافظه ذخیره و در درخواست‌های بعدی اعمال می‌شوند."
    override val driveLoopbackPkceButton = "ورود با مرورگر (Loopback PKCE خودکار)"
    override val driveLoopbackHint = "با Desktop OAuth Client ID نیازی به ثبت SHA-1 نیست و کد ورود به صورت خودکار دریافت می‌شود."
}

fun englishStrings(): AppStrings = EnglishStrings

fun persianStrings(): AppStrings = PersianStrings

val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
val LocalIsPersian = staticCompositionLocalOf { false }
