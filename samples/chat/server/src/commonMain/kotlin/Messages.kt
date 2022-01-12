package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.samples.chat.api.*

class Messages {
    private val storage = Storage<Message>()

    val values: List<Message> get() = storage.values()

    fun getOrNull(id: Int): Message? = storage.getOrNull(id)

    fun delete(id: Int) {
        storage.remove(id)
    }

    fun deleteForChat(chatId: Int) {
        storage.values().forEach {
            if (it.chatId == chatId) storage.remove(it.id)
        }
    }

    fun create(userId: Int, chatId: Int, content: String): Message {
        val messageId = storage.nextId()
        val message = Message(messageId, chatId, userId, currentMillis(), content)
        storage.save(messageId, message)
        return message
    }

    private fun byChatSorted(chatId: Int): List<Message> =
        storage.values().filter { it.chatId == chatId }.sortedByDescending { it.timestamp }

    fun takeLast(chatId: Int, limit: Int): List<Message> = byChatSorted(chatId).take(limit)

    fun takeAfter(chatId: Int, messageId: Int): List<Message> {
        val messages = byChatSorted(chatId)
        if (messageId == -1) return messages.take(1)

        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) error("No message with id '$messageId'")
        return messages.drop(index)
    }
}

operator fun Messages.get(id: Int): Message = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Messages.minusAssign(id: Int): Unit = delete(id)
