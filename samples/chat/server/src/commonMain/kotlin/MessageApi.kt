package io.rsocket.kotlin.samples.chat.server

import io.ktor.util.collections.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessageApiImpl(
    private val messages: Messages,
    private val chats: Chats,
) : MessageApi {

    private val listeners = ConcurrentList<SendChannel<Message>>()

    override suspend fun send(chatId: Int, content: String): Message {
        if (chatId !in chats) error("No chat with id '$chatId'")
        val userId = currentSession().userId
        val message = messages.create(userId, chatId, content)
        listeners.forEach { it.send(message) }
        return message
    }

    override suspend fun history(chatId: Int, limit: Int): List<Message> {
        if (chatId !in chats) error("No chat with id '$chatId'")
        return messages.takeLast(chatId, limit)
    }

    override fun messages(chatId: Int, fromMessageId: Int): Flow<Message> = flow {
        messages.takeAfter(chatId, fromMessageId).forEach { emit(it) }
        emitAll(channelFlow<Message> {
            listeners += channel
            awaitClose {
                listeners -= channel
            }
        }.buffer())
    }
}

