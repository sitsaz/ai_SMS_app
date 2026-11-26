package com.example

import android.app.Application
import com.example.data.ai.AiSearchClient
import com.example.data.ai.XmlArchiveBuilder
import com.example.data.local.SmsArchiveDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.search.ArchiveSearchRepository
import com.example.data.sms.ContactsRepository
import com.example.data.sms.SmsRepository
import com.example.notifications.NotificationRepository
import com.example.sms.SmsSenderManager

class AppContainer(application: Application) {
    val database: SmsArchiveDatabase by lazy { SmsArchiveDatabase.getInstance(application) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(application) }
    val smsSenderManager: SmsSenderManager by lazy { SmsSenderManager(application) }
    val smsRepository: SmsRepository by lazy {
        SmsRepository(
            context = application,
            smsDao = database.smsDao(),
            contactsRepository = contactsRepository,
            senderManager = smsSenderManager
        )
    }
    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(database.notificationDao())
    }
    val searchRepository: ArchiveSearchRepository by lazy {
        ArchiveSearchRepository(database.smsDao(), database.notificationDao())
    }
    val xmlArchiveBuilder: XmlArchiveBuilder by lazy {
        XmlArchiveBuilder(database.smsDao(), database.notificationDao())
    }
    val aiSearchClient: AiSearchClient by lazy { AiSearchClient() }
}

class AiSmsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
