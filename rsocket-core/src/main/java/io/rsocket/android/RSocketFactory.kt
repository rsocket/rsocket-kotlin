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

package io.rsocket.android

import io.reactivex.Completable
import io.reactivex.Single
import io.rsocket.android.exceptions.InvalidSetupException
import io.rsocket.android.fragmentation.FragmentationDuplexConnection
import io.rsocket.android.frame.SetupFrameFlyweight
import io.rsocket.android.frame.VersionFlyweight
import io.rsocket.android.internal.*
import io.rsocket.android.plugins.DuplexConnectionInterceptor
import io.rsocket.android.plugins.PluginRegistry
import io.rsocket.android.plugins.Plugins
import io.rsocket.android.plugins.RSocketInterceptor
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.transport.ServerTransport
import io.rsocket.android.util.PayloadImpl

/** Factory for creating RSocket clients and servers.  */
object RSocketFactory {

    private const val DEFAULT_STREAM_DEMAND_LIMIT = 128
    /**
     * Creates a factory that establishes client connections to other RSockets.
     *
     * @return a client factory
     */
    fun connect(): ClientRSocketFactory = ClientRSocketFactory()

    /**
     * Creates a factory that receives server connections from client RSockets.
     *
     * @return a server factory.
     */
    fun receive(): ServerRSocketFactory = ServerRSocketFactory()

    interface Start<T : Closeable> {
        fun start(): Single<T>
    }

    interface ClientTransportAcceptor {
        fun transport(transport: () -> ClientTransport): Start<RSocket>

        fun transport(transport: ClientTransport): Start<RSocket> = transport { transport }

    }

    interface ServerTransportAcceptor {
        fun <T : Closeable> transport(transport: () -> ServerTransport<T>): Start<T>

        fun <T : Closeable> transport(transport: ServerTransport<T>): Start<T> = transport({ transport })

    }

    class ClientRSocketFactory {

        private var acceptor: () -> (RSocket) -> RSocket = { { rs -> rs } }

        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val plugins = PluginRegistry(Plugins.defaultPlugins())
        private var flags = 0

        private var setupPayload: Payload = PayloadImpl.EMPTY

        private var tickPeriod = Duration.ZERO
        private var ackTimeout = Duration.ofSeconds(30)
        private var missedAcks = 3

        private var metadataMimeType = "application/binary"
        private var dataMimeType = "application/binary"

        private var streamDemandLimit = DEFAULT_STREAM_DEMAND_LIMIT

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

        fun keepAlive(): ClientRSocketFactory {
            tickPeriod = Duration.ofSeconds(20)
            return this
        }

        fun keepAlive(
                tickPeriod: Duration, ackTimeout: Duration, missedAcks: Int): ClientRSocketFactory {
            this.tickPeriod = tickPeriod
            this.ackTimeout = ackTimeout
            this.missedAcks = missedAcks
            return this
        }

        fun keepAliveTickPeriod(tickPeriod: Duration): ClientRSocketFactory {
            this.tickPeriod = tickPeriod
            return this
        }

        fun keepAliveAckTimeout(ackTimeout: Duration): ClientRSocketFactory {
            this.ackTimeout = ackTimeout
            return this
        }

        fun keepAliveMissedAcks(missedAcks: Int): ClientRSocketFactory {
            this.missedAcks = missedAcks
            return this
        }

        fun mimeType(metadataMimeType: String, dataMimeType: String): ClientRSocketFactory {
            this.dataMimeType = dataMimeType
            this.metadataMimeType = metadataMimeType
            return this
        }

        fun dataMimeType(dataMimeType: String): ClientRSocketFactory {
            this.dataMimeType = dataMimeType
            return this
        }

        fun metadataMimeType(metadataMimeType: String): ClientRSocketFactory {
            this.metadataMimeType = metadataMimeType
            return this
        }

        fun transport(transport: () -> ClientTransport): Start<RSocket> = StartClient(transport)

        fun acceptor(acceptor: () -> (RSocket) -> RSocket): ClientTransportAcceptor {
            this.acceptor = acceptor
            return object : ClientTransportAcceptor {
                override fun transport(transport: () -> ClientTransport): Start<RSocket> = StartClient(transport)

            }
        }

        fun fragment(mtu: Int): ClientRSocketFactory {
            this.mtu = mtu
            return this
        }

        fun errorConsumer(errorConsumer: (Throwable) -> Unit): ClientRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        fun setupPayload(payload: Payload): ClientRSocketFactory {
            this.setupPayload = payload
            return this
        }

        fun streamDemandLimit(streamDemandLimit: Int): ClientRSocketFactory {
            this.streamDemandLimit = streamDemandLimit
            return this
        }

        private inner class StartClient internal constructor(private val transportClient: () -> ClientTransport)
            : Start<RSocket> {

            override fun start(): Single<RSocket> {
                return transportClient()
                        .connect()
                        .flatMap { connection ->
                            val setupFrame = Frame.Setup.from(
                                    flags,
                                    ackTimeout.toMillis.toInt(),
                                    ackTimeout.toMillis.toInt() * missedAcks,
                                    metadataMimeType,
                                    dataMimeType,
                                    setupPayload)

                            val conn =
                                    if (mtu > 0)
                                        FragmentationDuplexConnection(connection, mtu)
                                    else
                                        connection

                            val demuxer = ClientConnectionDemuxer(conn, plugins)

                            val rSocketClient = RSocketClient(
                                    demuxer.requesterConnection(),
                                    errorConsumer,
                                    StreamIdSupplier.clientSupplier(),
                                    streamDemandLimit)

                            val wrappedRSocketClient = Single
                                    .just(rSocketClient)
                                    .map { plugins.applyClient(it) }

                            wrappedRSocketClient.flatMap { wrappedClientRSocket ->
                                val unwrappedServerSocket = acceptor()(wrappedClientRSocket)

                                val wrappedRSocketServer = Single
                                        .just<RSocket>(unwrappedServerSocket)
                                        .map { plugins.applyServer(it) }

                                wrappedRSocketServer
                                        .doOnSuccess { rSocket ->
                                            RSocketServer(
                                                    demuxer.responderConnection(),
                                                    rSocket,
                                                    errorConsumer,
                                                    streamDemandLimit)
                                        }.doOnSuccess {
                                            ClientServiceHandler(
                                                    demuxer.serviceConnection(),
                                                    errorConsumer,
                                                    KeepAliveInfo(
                                                            tickPeriod,
                                                            ackTimeout,
                                                            missedAcks))
                                        }
                                        .flatMapCompletable { conn.sendOne(setupFrame) }
                                        .andThen(wrappedRSocketClient)
                            }
                        }
            }
        }
    }

    class ServerRSocketFactory internal constructor() {

        private var acceptor: (() -> SocketAcceptor)? = null
        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val plugins = PluginRegistry(Plugins.defaultPlugins())
        private var streamDemandLimit = DEFAULT_STREAM_DEMAND_LIMIT

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

        fun acceptor(acceptor: () -> SocketAcceptor): ServerTransportAcceptor {
            this.acceptor = acceptor
            return object : ServerTransportAcceptor {
                override fun <T : Closeable> transport(transport: () -> ServerTransport<T>): Start<T> =
                        ServerStart(transport)
            }
        }

        fun fragment(mtu: Int): ServerRSocketFactory {
            this.mtu = mtu
            return this
        }

        fun errorConsumer(errorConsumer: (Throwable) -> Unit): ServerRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        fun streamDemandLimit(streamDemandLimit: Int): ServerRSocketFactory {
            this.streamDemandLimit = streamDemandLimit
            return this
        }

        private inner class ServerStart<T : Closeable> internal constructor(
                private val transportServer: () -> ServerTransport<T>) : Start<T> {

            override fun start(): Single<T> {
                return transportServer()
                        .start(object : ServerTransport.ConnectionAcceptor {
                            override fun invoke(conn: DuplexConnection): Completable {

                                val connection =
                                        if (mtu > 0)
                                            FragmentationDuplexConnection(conn, mtu)
                                        else conn

                                val demuxer = ServerConnectionDemuxer(connection, plugins)
                                return demuxer
                                        .setupConnection()
                                        .receive()
                                        .firstOrError()
                                        .flatMapCompletable { setupFrame ->
                                            processSetupFrame(demuxer, setupFrame)
                                        }
                            }
                        })
            }

            private fun processSetupFrame(
                    demuxer: ConnectionDemuxer, setupFrame: Frame): Completable {
                val version = Frame.Setup.version(setupFrame)
                if (version != SetupFrameFlyweight.CURRENT_VERSION) {
                    val error = InvalidSetupException(
                            "Unsupported version ${VersionFlyweight.toString(version)}")
                    return demuxer
                            .setupConnection()
                            .sendOne(Frame.Error.from(0, error))
                            .andThen { demuxer.close() }
                }

                val setupPayload = ConnectionSetupPayload.create(setupFrame)

                val rSocketClient = RSocketClient(
                        demuxer.requesterConnection(),
                        errorConsumer,
                        StreamIdSupplier.serverSupplier(),
                        streamDemandLimit)

                val wrappedRSocketClient = Single
                        .just(rSocketClient)
                        .map { plugins.applyClient(it) }

                return wrappedRSocketClient
                        .flatMap { requester ->
                            acceptor
                                    ?.let { it() }
                                    ?.accept(setupPayload, requester)
                                    ?.map { plugins.applyServer(it) }
                        }
                        .map { handler ->
                            RSocketServer(
                                    demuxer.responderConnection(),
                                    handler,
                                    errorConsumer,
                                    streamDemandLimit)
                        }.doOnSuccess {
                            ServerServiceHandler(
                                    demuxer.serviceConnection(),
                                    errorConsumer)
                        }.ignoreElement()
            }
        }
    }
}
