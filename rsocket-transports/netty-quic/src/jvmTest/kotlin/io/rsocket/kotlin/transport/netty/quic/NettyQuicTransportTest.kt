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

import io.netty.channel.nio.*
import io.netty.handler.ssl.util.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.transport.tests.*
import kotlin.concurrent.*

private val eventLoop = NioEventLoopGroup().also {
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        it.shutdownGracefully().await(1000)
    })
}
private val certificates = SelfSignedCertificate()

private val protos = arrayOf("hq-29")

class NettyQuicTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(
            NettyQuicServerTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    keyManager(certificates.privateKey(), null, certificates.certificate())
                    applicationProtocols(*protos)
                }
                codec {
                    tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    //maxIdleTimeout(1000, TimeUnit.MILLISECONDS)

                    initialMaxData(Int.MAX_VALUE.toLong())
                    initialMaxStreamDataBidirectionalLocal(Int.MAX_VALUE.toLong())
                    initialMaxStreamDataBidirectionalRemote(Int.MAX_VALUE.toLong())

//                    initialMaxData(100)
//                    initialMaxStreamDataBidirectionalLocal(100)
//                    initialMaxStreamDataBidirectionalRemote(100)
                }
            }.target("127.0.0.1")
        )
        client = connectClient(
            NettyQuicClientTransport(testContext) {
                eventLoopGroup(eventLoop, manage = false)
                ssl {
                    trustManager(InsecureTrustManagerFactory.INSTANCE)
                    applicationProtocols(*protos)
                }
                codec {
                    //maxIdleTimeout(1000, TimeUnit.MILLISECONDS)
                    initialMaxData(Int.MAX_VALUE.toLong())
                    initialMaxStreamDataBidirectionalLocal(Int.MAX_VALUE.toLong())
                    initialMaxStreamDataBidirectionalRemote(Int.MAX_VALUE.toLong())

//                    initialMaxData(100)
//                    initialMaxStreamDataBidirectionalLocal(100)
//                    initialMaxStreamDataBidirectionalRemote(100)
                }
            }.target(server.localAddress)
        )
    }
}
