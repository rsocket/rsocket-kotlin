package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
class MessageApiClient(private val rSocket: RSocket, private val proto: ProtoBuf) : MessageApi {
    override suspend fun send(chatId: Int, content: String): Message = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "messages.send", SendMessage(chatId, content))
        )
    )

    override suspend fun history(chatId: Int, limit: Int): List<Message> = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "messages.history", HistoryMessages(chatId, limit))
        )
    )

    override fun messages(chatId: Int, fromMessageId: Int): Flow<Message> = rSocket.requestStream(
        proto.encodeToPayload(route = "messages.stream", StreamMessages(chatId, fromMessageId))
    ).map {
        proto.decodeFromPayload(it)
    }
}
