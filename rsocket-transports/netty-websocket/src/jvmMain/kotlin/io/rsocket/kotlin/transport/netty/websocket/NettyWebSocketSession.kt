/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.transport.netty.websocket

import io.ktor.utils.io.core.*
import io.netty.buffer.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.websocketx.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyWebSocketSession(
    override val coroutineContext: CoroutineContext,
    private val channel: DuplexChannel,
    private val frames: ReceiveChannel<ByteReadPacket>,
) : RSocketTransportSession.Sequential {

    init {
        linkCompletionWith(channel)
    }

    override suspend fun sendFrame(frame: ByteReadPacket) {
        channel.writeAndFlush(BinaryWebSocketFrame(Unpooled.wrappedBuffer(frame.readByteBuffer()))).awaitFuture()
    }

    override suspend fun receiveFrame(): ByteReadPacket {
        return frames.receive()
    }
}
