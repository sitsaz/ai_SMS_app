package com.example.notifications

import com.example.data.local.NotificationItem
import com.example.data.local.NotificationDao
import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val notificationDao: NotificationDao) {

    fun observeAll(): Flow<List<NotificationItem>> = notificationDao.observeAll()

    suspend fun saveIfNotDuplicate(entity: NotificationItem) {
        val recent = notificationDao.findRecentDuplicate(
            key = entity.notificationKey,
            body = entity.body,
            sinceMs = entity.timestamp - 5_000L
        )
        if (recent == null) {
            notificationDao.insert(entity)
        }
    }

    suspend fun deleteById(id: Long) = notificationDao.deleteById(id)

    suspend fun clearAll() = notificationDao.clearAll()
}
