package com.example.data.drive

import android.content.Context
import com.example.data.local.NotificationDao
import com.example.data.local.NotificationItem
import com.example.data.local.SmsDao
import com.example.data.local.SmsMessage
import com.example.data.sms.ContactsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable
data class BackupSmsRecord(
    val systemSmsId: Long? = null,
    val threadId: Long,
    val sender: String = "",
    val address: String,
    val normalizedAddress: String = "",
    val contactName: String? = null,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val deliveryStatus: String = "NONE",
    val isRead: Boolean = true,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L
)

@Serializable
data class BackupNotificationRecord(
    val notificationKey: String,
    val threadId: String = "",
    val sender: String = "",
    val packageName: String,
    val appName: String,
    val senderOrTitle: String,
    val body: String,
    val subText: String = "",
    val extractedUrls: String = "",
    val timestamp: Long,
    val isRead: Boolean = false
)

@Serializable
data class DriveBackupPayload(
    val schemaVersion: Int = 1,
    val createdAtMs: Long,
    val includesSms: Boolean,
    val includesNotifications: Boolean,
    val smsMessages: List<BackupSmsRecord> = emptyList(),
    val notifications: List<BackupNotificationRecord> = emptyList()
)

data class DriveBackupFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val smsCount: Int,
    val notificationCount: Int,
    val includesSms: Boolean,
    val includesNotifications: Boolean
)

data class RestoreSummary(
    val restoredSmsCount: Int,
    val restoredNotificationCount: Int,
    val skippedDuplicatesCount: Int
)

class GoogleDriveBackupManager(
    private val smsDao: SmsDao,
    private val notificationDao: NotificationDao
) {

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val driveService: GoogleDriveApiService by lazy {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttp)
            .build()
            .create(GoogleDriveApiService::class.java)
    }

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_DRIVE_FILE),
                    Scope(SCOPE_DRIVE_APPDATA)
                )
            )
            .build()
    }

    suspend fun fetchAccountEmail(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = driveService.getAboutUser(bearerAuth = "Bearer $accessToken")
            val body = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive Auth HTTP ${response.code()}: ${extractApiError(body)}")
            }
            val user = JSONObject(body).optJSONObject("user")
            user?.optString("emailAddress").orEmpty()
        }
    }

    suspend fun listBackups(accessToken: String): Result<List<DriveBackupFileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "name contains '$BACKUP_FILE_PREFIX' and trashed = false"
            val response = driveService.listFiles(
                bearerAuth = "Bearer $accessToken",
                query = query
            )
            val body = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive List HTTP ${response.code()}: ${extractApiError(body)}")
            }
            val filesArr = JSONObject(body).optJSONArray("files") ?: JSONArray()
            val list = ArrayList<DriveBackupFileInfo>(filesArr.length())
            for (i in 0 until filesArr.length()) {
                val obj = filesArr.optJSONObject(i) ?: continue
                list.add(parseFileInfo(obj))
            }
            list
        }
    }

    suspend fun createAndUploadBackup(
        accessToken: String,
        includeSms: Boolean,
        includeNotifications: Boolean
    ): Result<DriveBackupFileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(includeSms || includeNotifications) {
                "Select at least SMS or Notifications to back up."
            }
            val payloadJson = exportLocalBackupJson(includeSms, includeNotifications)
            val payload = jsonCodec.decodeFromString<DriveBackupPayload>(payloadJson)
            val folderId = ensureBackupFolder(accessToken)

            val timestampTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(payload.createdAtMs))
            val fileName = "${BACKUP_FILE_PREFIX}$timestampTag.json"

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "application/json")
                if (folderId.isNotBlank()) {
                    put("parents", JSONArray().put(folderId))
                }
                put("appProperties", JSONObject().apply {
                    put("smsCount", payload.smsMessages.size.toString())
                    put("notificationCount", payload.notifications.size.toString())
                    put("includesSms", includeSms.toString())
                    put("includesNotifications", includeNotifications.toString())
                })
            }

            val metadataPart = MultipartBody.Part.createFormData(
                "metadata",
                null,
                metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            val mediaPart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                payloadJson.toRequestBody(jsonMediaType)
            )

            val response = driveService.uploadMultipartFile(
                bearerAuth = "Bearer $accessToken",
                metadata = metadataPart,
                media = mediaPart
            )
            val raw = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive Upload HTTP ${response.code()}: ${extractApiError(raw)}")
            }
            parseFileInfo(JSONObject(raw))
        }
    }

    suspend fun restoreBackup(
        accessToken: String,
        fileId: String,
        restoreSms: Boolean,
        restoreNotifications: Boolean
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(restoreSms || restoreNotifications) {
                "Select at least SMS or Notifications in backup scope to restore."
            }
            val response = driveService.downloadFile(
                bearerAuth = "Bearer $accessToken",
                fileId = fileId
            )
            val rawJson = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive Download HTTP ${response.code()}: ${extractApiError(rawJson)}")
            }
            importLocalBackupJson(rawJson, restoreSms, restoreNotifications).getOrThrow()
        }
    }

    suspend fun deleteBackup(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = driveService.deleteFile(
                bearerAuth = "Bearer $accessToken",
                fileId = fileId
            )
            if (!response.isSuccessful && response.code() != 404) {
                val err = response.errorBody()?.string().orEmpty()
                throw IllegalStateException("Google Drive Delete HTTP ${response.code()}: ${extractApiError(err)}")
            }
            Unit
        }
    }

    suspend fun exportLocalBackupJson(
        includeSms: Boolean,
        includeNotifications: Boolean
    ): String = withContext(Dispatchers.IO) {
        val smsRecords = if (includeSms) {
            smsDao.getAllForBackup().map { it.toBackupRecord() }
        } else {
            emptyList()
        }
        val notifRecords = if (includeNotifications) {
            notificationDao.getAllForBackup().map { it.toBackupRecord() }
        } else {
            emptyList()
        }
        val payload = DriveBackupPayload(
            schemaVersion = 1,
            createdAtMs = System.currentTimeMillis(),
            includesSms = includeSms,
            includesNotifications = includeNotifications,
            smsMessages = smsRecords,
            notifications = notifRecords
        )
        jsonCodec.encodeToString(payload)
    }

    suspend fun importLocalBackupJson(
        rawJson: String,
        restoreSms: Boolean,
        restoreNotifications: Boolean
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(restoreSms || restoreNotifications) {
                "Select at least SMS or Notifications to restore."
            }
            val payload = jsonCodec.decodeFromString<DriveBackupPayload>(rawJson)
            var restoredSms = 0
            var restoredNotifs = 0
            var skippedDuplicates = 0

            if (restoreSms) {
                val threadCache = HashMap<String, Long>()
                for (rec in payload.smsMessages) {
                    val normAddr = rec.normalizedAddress.ifBlank {
                        ContactsRepository.normalizeNumberKey(rec.address)
                    }
                    val existingCount = smsDao.countExactMatch(normAddr, rec.timestamp, rec.body)
                    if (existingCount > 0) {
                        skippedDuplicates++
                        continue
                    }
                    val resolvedThreadId = threadCache[normAddr]
                        ?: smsDao.findThreadIdByNormalizedAddress(normAddr)
                        ?: if (rec.threadId > 0L) rec.threadId else smsDao.nextSyntheticThreadId()
                    threadCache[normAddr] = resolvedThreadId

                    val insertedId = smsDao.insertMessage(
                        SmsMessage(
                            id = 0L,
                            systemSmsId = null, // Avoid colliding with device provider _id across devices
                            threadId = resolvedThreadId,
                            sender = rec.sender.ifBlank { rec.contactName ?: rec.address },
                            address = rec.address,
                            normalizedAddress = normAddr,
                            contactName = rec.contactName,
                            body = rec.body,
                            timestamp = rec.timestamp,
                            isOutgoing = rec.isOutgoing,
                            deliveryStatus = rec.deliveryStatus,
                            isRead = rec.isRead,
                            isPinned = rec.isPinned,
                            pinnedAt = rec.pinnedAt
                        )
                    )
                    if (insertedId > 0L) restoredSms++ else skippedDuplicates++
                }
            }

            if (restoreNotifications) {
                for (rec in payload.notifications) {
                    val existingCount = notificationDao.countExactMatch(
                        key = rec.notificationKey,
                        timestamp = rec.timestamp,
                        body = rec.body
                    )
                    if (existingCount > 0) {
                        skippedDuplicates++
                        continue
                    }
                    val id = notificationDao.insert(
                        NotificationItem(
                            id = 0L,
                            notificationKey = rec.notificationKey,
                            threadId = rec.threadId,
                            sender = rec.sender.ifBlank { rec.senderOrTitle },
                            packageName = rec.packageName,
                            appName = rec.appName,
                            senderOrTitle = rec.senderOrTitle,
                            body = rec.body,
                            subText = rec.subText,
                            extractedUrls = rec.extractedUrls,
                            timestamp = rec.timestamp,
                            isRead = rec.isRead
                        )
                    )
                    if (id > 0L) restoredNotifs++ else skippedDuplicates++
                }
            }

            RestoreSummary(
                restoredSmsCount = restoredSms,
                restoredNotificationCount = restoredNotifs,
                skippedDuplicatesCount = skippedDuplicates
            )
        }
    }

    private suspend fun ensureBackupFolder(accessToken: String): String {
        val q = "name = '$BACKUP_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val listResp = driveService.listFiles(
            bearerAuth = "Bearer $accessToken",
            query = q,
            fields = "files(id,name)",
            pageSize = 5
        )
        if (listResp.isSuccessful) {
            val raw = listResp.body()?.string().orEmpty()
            val files = runCatching { JSONObject(raw).optJSONArray("files") }.getOrNull()
            if (files != null && files.length() > 0) {
                val id = files.optJSONObject(0)?.optString("id").orEmpty()
                if (id.isNotBlank()) return id
            }
        }

        val folderMeta = JSONObject().apply {
            put("name", BACKUP_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createResp = driveService.createFolder(
            bearerAuth = "Bearer $accessToken",
            metadataBody = folderMeta.toString().toRequestBody(jsonMediaType)
        )
        if (createResp.isSuccessful) {
            val raw = createResp.body()?.string().orEmpty()
            return runCatching { JSONObject(raw).optString("id") }.getOrDefault("")
        }
        return ""
    }

    private fun parseFileInfo(obj: JSONObject): DriveBackupFileInfo {
        val props = obj.optJSONObject("appProperties")
        val smsCount = props?.optString("smsCount")?.toIntOrNull() ?: 0
        val notifCount = props?.optString("notificationCount")?.toIntOrNull() ?: 0
        val incSms = props?.optString("includesSms")?.toBooleanStrictOrNull() ?: (smsCount > 0)
        val incNotif = props?.optString("includesNotifications")?.toBooleanStrictOrNull() ?: (notifCount > 0)
        val modIso = obj.optString("modifiedTime")
        return DriveBackupFileInfo(
            id = obj.optString("id"),
            name = obj.optString("name"),
            sizeBytes = obj.optString("size").toLongOrNull() ?: 0L,
            modifiedAtMs = parseIso8601Millis(modIso),
            smsCount = smsCount,
            notificationCount = notifCount,
            includesSms = incSms,
            includesNotifications = incNotif
        )
    }

    private fun parseIso8601Millis(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis()
    }

    private fun extractApiError(rawBody: String): String {
        return runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }

    private fun SmsMessage.toBackupRecord(): BackupSmsRecord = BackupSmsRecord(
        systemSmsId = systemSmsId,
        threadId = threadId,
        sender = sender,
        address = address,
        normalizedAddress = normalizedAddress,
        contactName = contactName,
        body = body,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        deliveryStatus = deliveryStatus,
        isRead = isRead,
        isPinned = isPinned,
        pinnedAt = pinnedAt
    )

    private fun NotificationItem.toBackupRecord(): BackupNotificationRecord = BackupNotificationRecord(
        notificationKey = notificationKey,
        threadId = threadId,
        sender = sender,
        packageName = packageName,
        appName = appName,
        senderOrTitle = senderOrTitle,
        body = body,
        subText = subText,
        extractedUrls = extractedUrls,
        timestamp = timestamp,
        isRead = isRead
    )

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val BACKUP_FOLDER_NAME = "AI SMS Manager Backups"
        const val BACKUP_FILE_PREFIX = "ai_sms_backup_"
    }
}
