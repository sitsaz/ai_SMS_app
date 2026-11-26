package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MessagesRoute

@Serializable
data object SearchRoute

@Serializable
data object NotificationsRoute

@Serializable
data object SettingsRoute

@Serializable
data class AIChatRoute(
    val source: String = "",
    val threadId: Long = -1L,
    val topicId: Long = -1L
)

@Serializable
data class ThreadRoute(
    val threadId: Long,
    val address: String,
    val targetMessageId: Long = -1L
)
