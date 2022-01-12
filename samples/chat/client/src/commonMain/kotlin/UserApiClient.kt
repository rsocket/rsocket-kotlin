package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
class UserApiClient(private val rSocket: RSocket, private val proto: ProtoBuf) : UserApi {

    override suspend fun getMe(): User = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "users.getMe"))
    )

    override suspend fun deleteMe() {
        rSocket.fireAndForget(Payload(route = "users.deleteMe"))
    }

    override suspend fun all(): List<User> = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "users.all"))
    )
}
