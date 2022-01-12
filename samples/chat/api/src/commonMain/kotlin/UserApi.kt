package io.rsocket.kotlin.samples.chat.api

import kotlinx.serialization.*

interface UserApi {
    suspend fun getMe(): User
    suspend fun deleteMe()
    suspend fun all(): List<User>
}

@Serializable
data class User(
    val id: Int,
    val name: String,
)
