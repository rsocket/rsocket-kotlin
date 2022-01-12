package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.samples.chat.api.*

class Chats {
    private val storage = Storage<Chat>()

    val values: List<Chat> get() = storage.values()

    fun getOrNull(id: Int): Chat? = storage.getOrNull(id)

    fun delete(id: Int) {
        storage.remove(id)
    }

    fun create(name: String): Chat {
        if (storage.values().any { it.name == name }) error("Chat with such name already exists")
        val chatId = storage.nextId()
        val chat = Chat(chatId, name)
        storage.save(chatId, chat)
        return chat
    }

    fun exists(id: Int): Boolean = storage.contains(id)
}

operator fun Chats.get(id: Int): Chat = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Chats.minusAssign(id: Int): Unit = delete(id)
operator fun Chats.contains(id: Int): Boolean = exists(id)
