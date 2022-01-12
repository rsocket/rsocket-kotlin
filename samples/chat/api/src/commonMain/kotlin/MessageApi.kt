package io.rsocket.kotlin.samples.chat.api

import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

interface MessageApi {
    suspend fun send(chatId: Int, content: String): Message
    suspend fun history(chatId: Int, limit: Int = 10): List<Message>
    fun messages(chatId: Int, fromMessageId: Int): Flow<Message>
}

@Serializable
data class Message(
    val id: Int,
    val chatId: Int,
    val senderId: Int,
    val timestamp: Long,
    val content: String,
)

@Serializable
data class SendMessage(val chatId: Int, val content: String)

@Serializable
data class HistoryMessages(val chatId: Int, val limit: Int)

@Serializable
data class StreamMessages(val chatId: Int, val fromMessageId: Int)
