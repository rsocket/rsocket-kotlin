package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
class ApiClient(rSocket: RSocket) {
    private val proto = ConfiguredProtoBuf
    val users = UserApiClient(rSocket, proto)
    val chats = ChatApiClient(rSocket, proto)
    val messages = MessageApiClient(rSocket, proto)
}
