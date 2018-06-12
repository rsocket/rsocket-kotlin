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
import io.rsocket.kotlin.Closeable
import io.rsocket.kotlin.transport.ServerTransport
import io.rsocket.kotlin.transport.ServerTransport.ConnectionAcceptor
import io.rsocket.kotlin.transport.netty.WebsocketDuplexConnection
import io.rsocket.kotlin.transport.netty.toSingle
import reactor.ipc.netty.http.server.HttpServer
import reactor.ipc.netty.http.server.HttpServerRoutes

class WebsocketRouteTransport(private val server: HttpServer,
                              private val routesBuilder: (HttpServerRoutes) -> Unit,
                              private val path: String) : ServerTransport<Closeable> {

    override fun start(acceptor: ConnectionAcceptor): Single<Closeable> {
        return server
                .newRouter { routes ->
                    routesBuilder(routes)
                    routes.ws(path) { inbound, outbound ->
                        val connection = WebsocketDuplexConnection(
                                inbound,
                                outbound,
                                inbound.context())
                        acceptor(connection).andThen(outbound.neverComplete())
                    }
                }.toSingle().map { NettyContextCloseable(it) }
    }
}
