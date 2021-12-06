package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.samples.chat.api.*

class ChatApiImpl(
    private val chats: Chats,
    private val messages: Messages,
) : ChatApi {

    override suspend fun all(): List<Chat> = chats.values.toList()

    override suspend fun new(name: String): Chat = chats.create(name)

    override suspend fun delete(id: Int) {
        messages.deleteForChat(id)
        chats -= id
    }
}

