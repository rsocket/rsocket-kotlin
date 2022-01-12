package io.rsocket.kotlin.samples.chat.api

import kotlinx.serialization.*

interface ChatApi {
    suspend fun all(): List<Chat>
    suspend fun new(name: String): Chat
    suspend fun delete(id: Int)
}

@Serializable
data class Chat(
    val id: Int,
    val name: String,
)

@Serializable
data class NewChat(val name: String)

@Serializable
data class DeleteChat(val id: Int)
