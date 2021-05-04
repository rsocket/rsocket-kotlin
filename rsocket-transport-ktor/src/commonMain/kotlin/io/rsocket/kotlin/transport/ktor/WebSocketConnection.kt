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

package io.rsocket.kotlin.transport.ktor

import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import kotlinx.coroutines.*

@TransportApi
public class WebSocketConnection(private val session: WebSocketSession) : Connection {

    override val job: Job = session.coroutineContext.job

    override suspend fun send(packet: ByteReadPacket) {
        session.send(packet.readBytes())
    }

    override suspend fun receive(): ByteReadPacket {
        val frame = session.incoming.receive()
        return ByteReadPacket(frame.data)
    }

}
