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

package io.rsocket.android.transport.netty.server

import io.reactivex.Single
import io.rsocket.android.transport.ServerTransport
import io.rsocket.android.transport.netty.NettyDuplexConnection
import io.rsocket.android.transport.netty.RSocketLengthCodec
import io.rsocket.android.transport.netty.toSingle
import reactor.ipc.netty.tcp.TcpServer
import java.net.InetSocketAddress

class TcpServerTransport private constructor(private var server: TcpServer)
    : ServerTransport<NettyContextCloseable> {

    override fun start(acceptor: ServerTransport.ConnectionAcceptor)
            : Single<NettyContextCloseable> =
            server.newHandler { inbound, outbound ->
                inbound.context()
                        .addHandler(
                                "server-length-codec",
                                RSocketLengthCodec())
                val connection = NettyDuplexConnection(
                        inbound,
                        outbound,
                        inbound.context())
                acceptor(connection).subscribe()
                outbound.neverComplete()
            }.toSingle().map { NettyContextCloseable(it) }

    companion object {

        fun create(address: InetSocketAddress): TcpServerTransport {
            val server = TcpServer.create(address.hostName, address.port)
            return create(server)
        }

        fun create(bindAddress: String, port: Int): TcpServerTransport {
            val server = TcpServer.create(bindAddress, port)
            return create(server)
        }

        fun create(port: Int): TcpServerTransport {
            val server = TcpServer.create(port)
            return create(server)
        }

        fun create(server: TcpServer): TcpServerTransport {
            return TcpServerTransport(server)
        }
    }
}
