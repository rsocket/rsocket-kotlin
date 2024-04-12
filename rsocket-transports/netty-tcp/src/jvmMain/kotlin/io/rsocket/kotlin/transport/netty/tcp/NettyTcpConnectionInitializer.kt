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

package io.rsocket.kotlin.transport.netty.tcp

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyTcpConnectionInitializer(
    private val sslContext: SslContext?,
    private val remoteAddress: InetSocketAddress?,
    private val handler: RSocketConnectionHandler,
    override val coroutineContext: CoroutineContext,
) : ChannelInitializer<DuplexChannel>(), CoroutineScope {
    override fun initChannel(channel: DuplexChannel): Unit = with(channel.pipeline()) {
        if (sslContext != null) {
            addLast(
                "ssl",
                when {
                    remoteAddress != null -> sslContext.newHandler(channel.alloc(), remoteAddress.hostName, remoteAddress.port)
                    else                  -> sslContext.newHandler(channel.alloc())
                }
            )
        }
        addLast(
            "rsocket-length-encoder",
            LengthFieldPrepender(
                /* lengthFieldLength = */ 3
            )
        )
        addLast(
            "rsocket-length-decoder",
            LengthFieldBasedFrameDecoder(
                /* maxFrameLength = */ kotlin.Int.MAX_VALUE,
                /* lengthFieldOffset = */ 0,
                /* lengthFieldLength = */ 3,
                /* lengthAdjustment = */ 0,
                /* initialBytesToStrip = */ 3
            )
        )
        addLast("rsocket", NettyTcpConnectionHandler(channel, handler, this@NettyTcpConnectionInitializer))
    }
}
