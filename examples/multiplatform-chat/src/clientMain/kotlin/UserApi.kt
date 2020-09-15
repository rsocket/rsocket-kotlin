/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
actual class UserApi(private val rSocket: RSocket, private val proto: ProtoBuf) {

    actual suspend fun getMe(): User = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "users.getMe", ByteReadPacket.Empty))
    )

    actual suspend fun deleteMe() {
        rSocket.fireAndForget(Payload(route = "users.deleteMe", ByteReadPacket.Empty))
    }

    actual suspend fun all(): List<User> = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "users.all", ByteReadPacket.Empty))
    )
}
