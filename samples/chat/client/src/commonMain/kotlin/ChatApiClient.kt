package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
class ChatApiClient(private val rSocket: RSocket, private val proto: ProtoBuf) : ChatApi {
    override suspend fun all(): List<Chat> = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "chats.all"))
    )

    override suspend fun new(name: String): Chat = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "chats.new", NewChat(name))
        )
    )

    override suspend fun delete(id: Int) {
        rSocket.requestResponse(
            proto.encodeToPayload(route = "chats.delete", DeleteChat(id))
        ).close()
    }
}
