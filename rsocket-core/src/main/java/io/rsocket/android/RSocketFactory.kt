/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket

import io.reactivex.Completable
import io.reactivex.Single
import io.rsocket.android.exceptions.InvalidSetupException
import io.rsocket.android.fragmentation.FragmentationDuplexConnection
import io.rsocket.android.frame.SetupFrameFlyweight
import io.rsocket.android.frame.VersionFlyweight
import io.rsocket.android.internal.ClientServerInputMultiplexer
import io.rsocket.android.plugins.DuplexConnectionInterceptor
import io.rsocket.android.plugins.PluginRegistry
import io.rsocket.android.plugins.Plugins
import io.rsocket.android.plugins.RSocketInterceptor
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.transport.ServerTransport
import io.rsocket.android.util.PayloadImpl

/** Factory for creating RSocket clients and servers.  */
object RSocketFactory {
    /**
     * Creates a factory that establishes client connections to other RSockets.
     *
     * @return a client factory
     */
    fun connect(): ClientRSocketFactory {
        return ClientRSocketFactory()
    }

    /**
     * Creates a factory that receives server connections from client RSockets.
     *
     * @return a server factory.
     */
    fun receive(): ServerRSocketFactory {
        return ServerRSocketFactory()
    }

    interface Start<T : Closeable> {
        fun start(): Single<T>
    }

    interface SetupPayload<T> {
        fun setupPayload(payload: Payload): T
    }

    interface Acceptor<T, A> {
        fun acceptor(acceptor: () -> A): T

        fun acceptor(acceptor: A): T {
            return acceptor({ acceptor })
        }
    }

    interface ClientTransportAcceptor {
        fun transport(transport: () -> ClientTransport): Start<RSocket>

        fun transport(transport: ClientTransport): Start<RSocket> = transport({ transport })

    }

    interface ServerTransportAcceptor {
        fun <T : Closeable> transport(transport: () -> ServerTransport<T>): Start<T>

        fun <T : Closeable> transport(transport: ServerTransport<T>): Start<T> = transport({ transport })

    }

    interface Fragmentation<T> {
        fun fragment(mtu: Int): T
    }

    interface ErrorConsumer<T> {
        fun errorConsumer(errorConsumer: (Throwable) -> Unit): T
    }

    interface KeepAlive<T> {
        fun keepAlive(): T

        fun keepAlive(tickPeriod: Duration, ackTimeout: Duration, missedAcks: Int): T

        fun keepAliveTickPeriod(tickPeriod: Duration): T

        fun keepAliveAckTimeout(ackTimeout: Duration): T

        fun keepAliveMissedAcks(missedAcks: Int): T
    }

    interface MimeType<T> {
        fun mimeType(metadataMimeType: String, dataMimeType: String): T

        fun dataMimeType(dataMimeType: String): T

        fun metadataMimeType(metadataMimeType: String): T
    }

    class ClientRSocketFactory : Acceptor<ClientTransportAcceptor, (RSocket)->  RSocket>,
            ClientTransportAcceptor, KeepAlive<ClientRSocketFactory>,
            MimeType<ClientRSocketFactory>, Fragmentation<ClientRSocketFactory>,
            ErrorConsumer<ClientRSocketFactory>,
            SetupPayload<ClientRSocketFactory> {

        private var acceptor: () -> (RSocket) -> RSocket = { { rs -> rs } }

        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val plugins = PluginRegistry(Plugins.defaultPlugins())
        private val flags = SetupFrameFlyweight.FLAGS_STRICT_INTERPRETATION

        private var setupPayload: Payload = PayloadImpl.EMPTY

        private var tickPeriod = Duration.ZERO
        private var ackTimeout = Duration.ofSeconds(30)
        private var missedAcks = 3

        private var metadataMimeType = "application/binary"
        private var dataMimeType = "application/binary"

        fun addConnectionPlugin(interceptor: DuplexConnectionInterceptor): ClientRSocketFactory {
            plugins.addConnectionPlugin(interceptor)
            return this
        }

        fun addClientPlugin(interceptor: RSocketInterceptor): ClientRSocketFactory {
            plugins.addClientPlugin(interceptor)
            return this
        }

        fun addServerPlugin(interceptor: RSocketInterceptor): ClientRSocketFactory {
            plugins.addServerPlugin(interceptor)
            return this
        }

        override fun keepAlive(): ClientRSocketFactory {
            tickPeriod = Duration.ofSeconds(20)
            return this
        }

        override fun keepAlive(
                tickPeriod: Duration, ackTimeout: Duration, missedAcks: Int): ClientRSocketFactory {
            this.tickPeriod = tickPeriod
            this.ackTimeout = ackTimeout
            this.missedAcks = missedAcks
            return this
        }

        override fun keepAliveTickPeriod(tickPeriod: Duration): ClientRSocketFactory {
            this.tickPeriod = tickPeriod
            return this
        }

        override fun keepAliveAckTimeout(ackTimeout: Duration): ClientRSocketFactory {
            this.ackTimeout = ackTimeout
            return this
        }

        override fun keepAliveMissedAcks(missedAcks: Int): ClientRSocketFactory {
            this.missedAcks = missedAcks
            return this
        }

        override fun mimeType(metadataMimeType: String, dataMimeType: String): ClientRSocketFactory {
            this.dataMimeType = dataMimeType
            this.metadataMimeType = metadataMimeType
            return this
        }

        override fun dataMimeType(dataMimeType: String): ClientRSocketFactory {
            this.dataMimeType = dataMimeType
            return this
        }

        override fun metadataMimeType(metadataMimeType: String): ClientRSocketFactory {
            this.metadataMimeType = metadataMimeType
            return this
        }

        override fun transport(transport: () -> ClientTransport): Start<RSocket> {
            return StartClient(transport)
        }

        override fun acceptor(acceptor: () -> (RSocket) ->  RSocket): ClientTransportAcceptor {
            this.acceptor = acceptor
            return object : ClientTransportAcceptor {
                override fun transport(transport: () -> ClientTransport): Start<RSocket> = StartClient(transport)

            }
        }

        override fun fragment(mtu: Int): ClientRSocketFactory {
            this.mtu = mtu
            return this
        }

        override fun errorConsumer(errorConsumer: (Throwable) -> Unit): ClientRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        override fun setupPayload(payload: Payload): ClientRSocketFactory {
            this.setupPayload = payload
            return this
        }

        protected inner class StartClient internal constructor(private val transportClient: () -> ClientTransport) : Start<RSocket> {

            override fun start(): Single<RSocket> {
                return transportClient()
                        .connect()
                        .flatMap { connection ->
                            var conn = connection
                            val setupFrame = Frame.Setup.from(
                                    flags,
                                    ackTimeout.toMillis.toInt(),
                                    ackTimeout.toMillis.toInt() * missedAcks,
                                    metadataMimeType,
                                    dataMimeType,
                                    setupPayload)

                            if (mtu > 0) {
                                conn = FragmentationDuplexConnection(conn, mtu)
                            }

                            val multiplexer = ClientServerInputMultiplexer(conn, plugins)

                            val rSocketClient = RSocketClient(
                                    multiplexer.asClientConnection(),
                                    errorConsumer,
                                    StreamIdSupplier.clientSupplier(),
                                    tickPeriod,
                                    ackTimeout,
                                    missedAcks)

                            val wrappedRSocketClient = Single.just(rSocketClient).map { plugins.applyClient(it) }

                            wrappedRSocketClient.flatMap { wrappedClientRSocket ->
                                val unwrappedServerSocket = acceptor()(wrappedClientRSocket)

                                val wrappedRSocketServer = Single.just<RSocket>(unwrappedServerSocket).map { plugins.applyServer(it) }

                                wrappedRSocketServer
                                        .doAfterSuccess { rSocket ->
                                            RSocketServer(
                                                    multiplexer.asServerConnection(), rSocket, errorConsumer)
                                        }.flatMapCompletable { conn.sendOne(setupFrame) }
                                        .andThen (wrappedRSocketClient)
                            }
                        }
            }
        }
    }

    class ServerRSocketFactory internal constructor() : Acceptor<ServerTransportAcceptor, SocketAcceptor>,
            Fragmentation<ServerRSocketFactory>,
            ErrorConsumer<ServerRSocketFactory> {

        private var acceptor: (() -> SocketAcceptor)? = null
        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val plugins = PluginRegistry(Plugins.defaultPlugins())

        fun addConnectionPlugin(interceptor: DuplexConnectionInterceptor): ServerRSocketFactory {
            plugins.addConnectionPlugin(interceptor)
            return this
        }

        fun addClientPlugin(interceptor: RSocketInterceptor): ServerRSocketFactory {
            plugins.addClientPlugin(interceptor)
            return this
        }

        fun addServerPlugin(interceptor: RSocketInterceptor): ServerRSocketFactory {
            plugins.addServerPlugin(interceptor)
            return this
        }

        override fun acceptor(acceptor: () -> SocketAcceptor): ServerTransportAcceptor {
            this.acceptor = acceptor
            return object : ServerTransportAcceptor {
                override fun <T : Closeable> transport(transport: () -> ServerTransport<T>): Start<T> {
                    return ServerStart(transport)
                }
            }
        }

        override fun fragment(mtu: Int): ServerRSocketFactory {
            this.mtu = mtu
            return this
        }

        override fun errorConsumer(errorConsumer: (Throwable) -> Unit): ServerRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        private inner class ServerStart<T : Closeable> internal constructor(private val transportServer: () -> ServerTransport<T>) : Start<T> {

            override fun start(): Single<T> {
                return transportServer()
                        .start(object : ServerTransport.ConnectionAcceptor {
                            override fun invoke(duplexConnection: DuplexConnection): Completable {
                                var conn = duplexConnection
                                if (mtu > 0) {
                                    conn = FragmentationDuplexConnection(conn, mtu)
                                }

                                val multiplexer = ClientServerInputMultiplexer(conn, plugins)

                                return multiplexer
                                        .asStreamZeroConnection()
                                        .receive()
                                        .firstOrError()
                                        .flatMapCompletable { setupFrame -> processSetupFrame(multiplexer, setupFrame) }
                            }
                        })
            }

            private fun processSetupFrame(
                    multiplexer: ClientServerInputMultiplexer, setupFrame: Frame): Completable {
                val version = Frame.Setup.version(setupFrame)
                if (version != SetupFrameFlyweight.CURRENT_VERSION) {
                    val error = InvalidSetupException(
                            "Unsupported version " + VersionFlyweight.toString(version))
                    return multiplexer
                            .asStreamZeroConnection()
                            .sendOne(Frame.Error.from(0, error))
                            .andThen { multiplexer.close()}
                }

                val setupPayload = ConnectionSetupPayload.create(setupFrame)

                val rSocketClient = RSocketClient(
                        multiplexer.asServerConnection(), errorConsumer, StreamIdSupplier.serverSupplier())

                val wrappedRSocketClient = Single.just(rSocketClient).map { plugins.applyClient(it) }

                return wrappedRSocketClient
                        .flatMap { sender -> acceptor?.let { it() }?.accept(setupPayload, sender)?.map { plugins.applyServer(it) } }
                        .map { handler -> RSocketServer(multiplexer.asClientConnection(), handler, errorConsumer) }
                        .toCompletable()
            }
        }
    }
}
