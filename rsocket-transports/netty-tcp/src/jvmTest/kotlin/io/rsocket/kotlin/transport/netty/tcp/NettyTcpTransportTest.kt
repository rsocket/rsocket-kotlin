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

class NettyTcpTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(
            NettyTcpServerTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
            }.target()
        )
        client = connectClient(
            NettyTcpClientTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
            }.target(server.localAddress)
        )
    }
}

class NettyTcpSslTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(
            NettyTcpServerTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    keyManager(certificates.certificate(), certificates.privateKey())
                }
            }.target()
        )
        client = connectClient(
            NettyTcpClientTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    trustManager(InsecureTrustManagerFactory.INSTANCE)
                }
            }.target(server.localAddress)
        )
    }
}
