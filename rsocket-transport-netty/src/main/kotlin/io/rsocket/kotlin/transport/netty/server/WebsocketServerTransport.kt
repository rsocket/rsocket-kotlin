/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.transport.netty.server

import io.reactivex.Single
import io.rsocket.kotlin.transport.ServerTransport
import io.rsocket.kotlin.transport.TransportHeaderAware
import io.rsocket.kotlin.transport.netty.WebsocketDuplexConnection
import io.rsocket.kotlin.transport.netty.toSingle
import reactor.ipc.netty.http.server.HttpServer

class WebsocketServerTransport private constructor(internal var server: HttpServer)
    : ServerTransport<NettyContextCloseable>, TransportHeaderAware {
    private var transportHeaders: () -> Map<String, String> = { emptyMap() }

    override fun start(acceptor: ServerTransport.ConnectionAcceptor)
            : Single<NettyContextCloseable> {
        return server
                .newHandler { _, response ->
                    transportHeaders()
                            .forEach { name, value -> response.addHeader(name, value) }
                    response.sendWebsocket { inbound, outbound ->
                        val connection =
                                WebsocketDuplexConnection(
                                        inbound,
                                        outbound,
                                        inbound.context())
                        acceptor(connection).subscribe()
                        outbound.neverComplete()
                    }
                }.toSingle().map { NettyContextCloseable(it) }
    }

    override fun setTransportHeaders(transportHeaders: () -> Map<String, String>) {
        this.transportHeaders = transportHeaders
    }

    companion object {

        fun create(bindAddress: String, port: Int): WebsocketServerTransport {
            val httpServer = HttpServer.create(bindAddress, port)
            return create(httpServer)
        }

        fun create(port: Int): WebsocketServerTransport {
            val httpServer = HttpServer.create(port)
            return create(httpServer)
        }

        fun create(server: HttpServer): WebsocketServerTransport {
            return WebsocketServerTransport(server)
        }
    }
}
