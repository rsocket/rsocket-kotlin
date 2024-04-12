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

package io.rsocket.kotlin.transport.netty.quic

import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyQuicStreamInitializer(
    override val coroutineContext: CoroutineContext,
    private val state: NettyQuicStreamState,
    private val isClient: Boolean,
) : ChannelInitializer<QuicStreamChannel>(), CoroutineScope {
    override fun initChannel(channel: QuicStreamChannel): Unit = with(channel.pipeline()) {
        addLast(
            "rsocket-length-encoder",
            LengthFieldPrepender(
                /* lengthFieldLength = */ 3
            )
        )
        addLast(
            "rsocket-length-decoder",
            LengthFieldBasedFrameDecoder(
                /* maxFrameLength = */ Int.MAX_VALUE,
                /* lengthFieldOffset = */ 0,
                /* lengthFieldLength = */ 3,
                /* lengthAdjustment = */ 0,
                /* initialBytesToStrip = */ 3
            )
        )
        addLast("rsocket", NettyQuicStreamHandler(channel, this@NettyQuicStreamInitializer, state, isClient))
    }
}
