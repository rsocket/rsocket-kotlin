/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport.netty.tcp

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(RSocketTransportApi::class)
internal open class NettyTcpConnectionChannelInitializer(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
) : ChannelInitializer<DuplexChannel>(), CoroutineScope {
    override fun initChannel(channel: DuplexChannel) {
        channel.config().isAutoRead = false

        val connection = NettyTcpConnection(coroutineContext, channel)
        channel.attr(NettyTcpConnection.ATTRIBUTE).set(connection)

        if (sslContext != null) {
            channel.pipeline().addLast("ssl", sslContext.newHandler(channel.alloc()))
        }
        channel.pipeline().addLast(
            "rsocket-length-encoder",
            LengthFieldPrepender(
                /* lengthFieldLength = */ 3
            )
        )
        channel.pipeline().addLast(
            "rsocket-length-decoder",
            LengthFieldBasedFrameDecoder(
                /* maxFrameLength = */ Int.MAX_VALUE,
                /* lengthFieldOffset = */ 0,
                /* lengthFieldLength = */ 3,
                /* lengthAdjustment = */ 0,
                /* initialBytesToStrip = */ 3
            )
        )
        channel.pipeline().addLast(
            "rsocket-connection",
            connection
        )
    }
}
