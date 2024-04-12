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

import io.netty.channel.nio.*
import io.netty.handler.ssl.util.*
import io.rsocket.kotlin.transport.tests.*
import kotlin.concurrent.*

private val eventLoop = NioEventLoopGroup().also {
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        it.shutdownGracefully().await(1000)
    })
}
private val certificates = SelfSignedCertificate()

// TODO: add tests for paths
class NettyWebSocketTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(
            NettyWebSocketServerTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
            }.target()
        )
        client = connectClient(
            NettyWebSocketClientTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
            }.target(port = server.localAddress.port)
        )
    }
}

class NettyWebSocketSslTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(
            NettyWebSocketServerTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    keyManager(certificates.certificate(), certificates.privateKey())
                }
            }.target()
        )
        client = connectClient(
            NettyWebSocketClientTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    trustManager(InsecureTrustManagerFactory.INSTANCE)
                }
            }.target(port = server.localAddress.port)
        )
    }
}
