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

package io.rsocket.kotlin.transport.netty.client

import io.reactivex.Single
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.transport.ClientTransport
import io.rsocket.kotlin.transport.TransportHeaderAware
import io.rsocket.kotlin.transport.netty.WebsocketDuplexConnection
import io.rsocket.kotlin.transport.netty.toMono
import reactor.ipc.netty.http.client.HttpClient
import java.net.InetSocketAddress
import java.net.URI

class WebsocketClientTransport private constructor(private val client: HttpClient,
                                                   private val path: String)
    : ClientTransport, TransportHeaderAware {
    private var transportHeaders: () -> Map<String, String> = { emptyMap() }

    override fun connect(): Single<DuplexConnection> {
        return Single.create<DuplexConnection> { sink ->
            client.ws(path) { hb ->
                transportHeaders().forEach { name, value -> hb.set(name, value) }
            }.flatMap { response ->
                response.receiveWebsocket { inbound, outbound ->
                    val connection = WebsocketDuplexConnection(
                            inbound,
                            outbound,
                            inbound.context())
                    sink.onSuccess(connection)
                    connection.onClose().toMono()
                }
            }.doOnError { sink.onError(it) }.subscribe()
        }
    }

    override fun setTransportHeaders(transportHeaders: () -> Map<String, String>) {
        this.transportHeaders = transportHeaders
    }

    companion object {

        fun create(port: Int): WebsocketClientTransport {
            val httpClient = HttpClient.create(port)
            return create(httpClient, "/")
        }

        fun create(bindAddress: String, port: Int): WebsocketClientTransport {
            val httpClient = HttpClient.create(bindAddress, port)
            return create(httpClient, "/")
        }

        fun create(address: InetSocketAddress): WebsocketClientTransport {
            return create(address.hostName, address.port)
        }

        fun create(uri: URI): WebsocketClientTransport {
            val httpClient = createClient(uri)
            return create(httpClient, uri.toString())
        }

        private fun createClient(uri: URI): HttpClient {
            return if (isSecureWebsocket(uri)) {
                HttpClient.create { options ->
                    options.sslSupport()
                            .connectAddress {
                                InetSocketAddress.createUnresolved(
                                        uri.host,
                                        getPort(uri, 443))
                            }
                }
            } else {
                HttpClient.create(uri.host, getPort(uri, 80))
            }
        }

        fun getPort(uri: URI, defaultPort: Int): Int {
            return if (uri.port == -1) defaultPort else uri.port
        }

        fun isSecureWebsocket(uri: URI): Boolean {
            return uri.scheme == "wss" || uri.scheme == "https"
        }

        fun isPlaintextWebsocket(uri: URI): Boolean {
            return uri.scheme == "ws" || uri.scheme == "http"
        }

        fun create(client: HttpClient, path: String): WebsocketClientTransport {
            return WebsocketClientTransport(client, path)
        }
    }
}
