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

package io.rsocket.android.transport.netty.client

import io.reactivex.Single
import io.rsocket.android.DuplexConnection
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.transport.netty.NettyDuplexConnection
import io.rsocket.android.transport.netty.RSocketLengthCodec
import io.rsocket.android.transport.netty.toMono
import reactor.ipc.netty.tcp.TcpClient
import java.net.InetSocketAddress

class TcpClientTransport private constructor(private val client: TcpClient)
    : ClientTransport {

    override fun connect(): Single<DuplexConnection> =
            Single.create<DuplexConnection> { sink ->
                client.newHandler { inbound, outbound ->
                    inbound.context().addHandler(
                            "client-length-codec",
                            RSocketLengthCodec())

                    val connection = NettyDuplexConnection(
                            inbound,
                            outbound,
                            inbound.context())
                    sink.onSuccess(connection)
                    connection.onClose().toMono()
                }.doOnError { sink.onError(it) }.subscribe()
            }

    companion object {

        fun create(port: Int): TcpClientTransport {
            val tcpClient = TcpClient.create(port)
            return create(tcpClient)
        }

        fun create(bindAddress: String, port: Int): TcpClientTransport {
            val tcpClient = TcpClient.create(bindAddress, port)
            return create(tcpClient)
        }

        fun create(address: InetSocketAddress): TcpClientTransport {
            val tcpClient = TcpClient.create(address.hostString, address.port)
            return create(tcpClient)
        }

        fun create(client: TcpClient): TcpClientTransport {
            return TcpClientTransport(client)
        }
    }
}
