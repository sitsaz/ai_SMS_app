package com.example

import android.app.Application
import com.example.data.ai.AiSearchClient
import com.example.data.ai.DynamicAiRetrofitProvider
import com.example.data.ai.XmlArchiveBuilder
import com.example.data.archive.ChatArchiveRepository
import com.example.data.drive.BackupManager
import com.example.data.drive.GoogleDriveBackupManager
import com.example.data.local.SmsArchiveDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.search.ArchiveSearchRepository
import com.example.data.sms.ContactsRepository
import com.example.data.sms.SmsRepository
import com.example.notifications.NotificationRepository
import com.example.sms.SmsProviderObserver
import com.example.sms.SmsSenderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: SmsArchiveDatabase by lazy { SmsArchiveDatabase.getInstance(application) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(application) }
    val smsSenderManager: SmsSenderManager by lazy { SmsSenderManager(application) }
    val smsRepository: SmsRepository by lazy {
        SmsRepository(
            context = application,
            smsDao = database.smsDao(),
            smsThreadDao = database.smsThreadDao(),
            contactsRepository = contactsRepository,
            senderManager = smsSenderManager,
            settingsRepository = settingsRepository
        )
    }
    val smsProviderObserver: SmsProviderObserver by lazy {
        SmsProviderObserver(application, smsRepository)
    }
    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(database.notificationDao())
    }
    val searchRepository: ArchiveSearchRepository by lazy {
        ArchiveSearchRepository(database.smsDao(), database.notificationDao(), database.searchHistoryDao())
    }
    val chatArchiveRepository: ChatArchiveRepository by lazy {
        ChatArchiveRepository(
            smsDao = database.smsDao(),
            smsThreadDao = database.smsThreadDao(),
            aiTopicDao = database.aiTopicDao()
        )
    }
    val xmlArchiveBuilder: XmlArchiveBuilder by lazy {
        XmlArchiveBuilder(database.smsDao(), database.notificationDao())
    }
    val dynamicAiRetrofitProvider: DynamicAiRetrofitProvider by lazy {
        DynamicAiRetrofitProvider()
    }
    val aiSearchClient: AiSearchClient by lazy {
        AiSearchClient(dynamicAiRetrofitProvider)
    }
    val googleDriveBackupManager: GoogleDriveBackupManager by lazy {
        GoogleDriveBackupManager(database.smsDao(), database.notificationDao())
    }
    val backupManager: BackupManager by lazy {
        BackupManager(
            context = application,
            googleDriveBackupManager = googleDriveBackupManager,
            settingsRepository = settingsRepository
        )
    }
}

class AiSmsApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.smsProviderObserver.registerIfPermitted()
        appScope.launch {
            runCatching { container.backupManager.ensureScheduleMatchesSettings() }
        }
    }
}
