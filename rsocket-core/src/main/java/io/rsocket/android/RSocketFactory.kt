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
import io.rsocket.android.fragmentation.FragmentationInterceptor
import io.rsocket.android.internal.*
import io.rsocket.android.plugins.GlobalInterceptors
import io.rsocket.android.plugins.InterceptorOptions
import io.rsocket.android.plugins.InterceptorRegistry
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.transport.ServerTransport
import io.rsocket.android.util.KeepAlive
import io.rsocket.android.util.KeepAliveOptions
import io.rsocket.android.util.MediaTypeOptions
import io.rsocket.android.util.PayloadImpl

/** Factory for creating RSocket clients and servers.  */
object RSocketFactory {

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

    class ClientRSocketFactory {

        private var acceptor: ClientAcceptor = { { emptyRSocket } }
        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val interceptors = GlobalInterceptors.create()
        private var flags = 0
        private var setupPayload: Payload = PayloadImpl.EMPTY
        private val keepAlive = KeepAliveOptions()
        private val mediaType = MediaTypeOptions()
        private var streamRequestLimit = defaultStreamRequestLimit

        fun interceptors(configure: (InterceptorOptions) -> Unit): ClientRSocketFactory {
            configure(interceptors)
            return this
        }

        fun keepAlive(configure: (KeepAliveOptions) -> Unit): ClientRSocketFactory {
            configure(keepAlive)
            return this
        }

        fun mimeType(configure: (MediaTypeOptions) -> Unit)
                : ClientRSocketFactory {
            configure(mediaType)
            return this
        }

        fun fragment(mtu: Int): ClientRSocketFactory {
            assertFragmentation(mtu)
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

        fun streamRequestLimit(streamRequestLimit: Int): ClientRSocketFactory {
            assertRequestLimit(streamRequestLimit)
            this.streamRequestLimit = streamRequestLimit
            return this
        }

        fun transport(transport: () -> ClientTransport): Start<RSocket> =
                ClientStart(transport, interceptors())

        fun acceptor(acceptor: ClientAcceptor): ClientTransportAcceptor {
            this.acceptor = acceptor
            return object : ClientTransportAcceptor {
                override fun transport(transport: () -> ClientTransport)
                        : Start<RSocket> =
                        ClientStart(transport, interceptors())
            }
        }

        private fun interceptors(): InterceptorRegistry =
                interceptors.copyWith {
                    it.connectionFirst(
                            FragmentationInterceptor(mtu))
                }

        private inner class ClientStart(private val transportClient: () -> ClientTransport,
                                        private val interceptors: InterceptorRegistry)
            : Start<RSocket> {

            override fun start(): Single<RSocket> {
                return transportClient()
                        .connect()
                        .flatMap { connection ->
                            val setupFrame = createSetupFrame()

                            val demuxer = ClientConnectionDemuxer(
                                    connection,
                                    interceptors)

                            val rSocketRequester = RSocketRequester(
                                    demuxer.requesterConnection(),
                                    errorConsumer,
                                    ClientStreamIds(),
                                    streamRequestLimit)

                            val wrappedRequester = interceptors
                                    .interceptRequester(rSocketRequester)

                            val handlerRSocket = acceptor()(wrappedRequester)

                            val wrappedHandler = interceptors
                                    .interceptHandler(handlerRSocket)

                            RSocketResponder(
                                    demuxer.responderConnection(),
                                    wrappedHandler,
                                    errorConsumer,
                                    streamRequestLimit)

                            ClientServiceHandler(
                                    demuxer.serviceConnection(),
                                    keepAlive,
                                    errorConsumer)

                            connection
                                    .sendOne(setupFrame)
                                    .andThen(Single.just(wrappedRequester))
                        }
            }

            private fun createSetupFrame(): Frame {
                return Frame.Setup.from(
                        flags,
                        keepAlive.keepAliveInterval().intMillis,
                        keepAlive.keepAliveMaxLifeTime().intMillis,
                        mediaType.metadataMimeType(),
                        mediaType.dataMimeType(),
                        setupPayload)
            }
        }
    }

    class ServerRSocketFactory internal constructor() {

        private var acceptor: ServerAcceptor = { { _, _ -> Single.just(emptyRSocket) } }
        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val interceptors = GlobalInterceptors.create()
        private var streamRequestLimit = defaultStreamRequestLimit

        fun interceptors(configure: (InterceptorOptions) -> Unit): ServerRSocketFactory {
            configure(interceptors)
            return this
        }

        fun fragment(mtu: Int): ServerRSocketFactory {
            assertFragmentation(mtu)
            this.mtu = mtu
            return this
        }

        fun errorConsumer(errorConsumer: (Throwable) -> Unit): ServerRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        fun streamRequestLimit(streamRequestLimit: Int): ServerRSocketFactory {
            this.streamRequestLimit = streamRequestLimit
            return this
        }

        fun acceptor(acceptor: ServerAcceptor): ServerTransportAcceptor {
            this.acceptor = acceptor
            return object : ServerTransportAcceptor {
                override fun <T : Closeable> transport(
                        transport: () -> ServerTransport<T>): Start<T> =
                        ServerStart(transport, interceptors())
            }
        }

        private fun interceptors(): InterceptorRegistry {
            return interceptors.copyWith {
                it.connectionFirst(
                        ServerContractInterceptor(errorConsumer))
                it.connectionFirst(
                        FragmentationInterceptor(mtu))
            }
        }

        private inner class ServerStart<T : Closeable>(
                private val transportServer: () -> ServerTransport<T>,
                private val interceptors: InterceptorRegistry) : Start<T> {

            override fun start(): Single<T> {
                return transportServer().start(object
                    : ServerTransport.ConnectionAcceptor {

                    override fun invoke(duplexConnection: DuplexConnection)
                            : Completable {

                        val demuxer = ServerConnectionDemuxer(
                                duplexConnection,
                                interceptors)

                        return demuxer
                                .setupConnection()
                                .receive()
                                .firstOrError()
                                .flatMapCompletable { setup ->
                                    accept(setup, demuxer)
                                }
                    }
                })
            }

            private fun accept(setupFrame: Frame,
                               demuxer: ConnectionDemuxer): Completable {

                val setup = Setup.create(setupFrame)

                val rSocketRequester = RSocketRequester(
                        demuxer.requesterConnection(),
                        errorConsumer,
                        ServerStreamIds(),
                        streamRequestLimit)

                val wrappedRequester =
                        interceptors.interceptRequester(rSocketRequester)

                ServerServiceHandler(
                        demuxer.serviceConnection(),
                        setup as KeepAlive,
                        errorConsumer)

                val handlerRSocket = acceptor()(setup, wrappedRequester)

                return handlerRSocket
                        .map { handler -> interceptors.interceptHandler(handler) }
                        .doOnSuccess { handler ->
                            RSocketResponder(
                                    demuxer.responderConnection(),
                                    handler,
                                    errorConsumer,
                                    streamRequestLimit)
                        }
                        .ignoreElement()
            }
        }
    }

    private fun assertRequestLimit(streamRequestLimit: Int) {
        if (streamRequestLimit <= 0) {
            throw IllegalArgumentException("stream request limit must be positive")
        }
    }

    private fun assertFragmentation(mtu: Int) {
        if (mtu < 0) {
            throw IllegalArgumentException("fragmentation mtu must be non-negative")
        }
    }

    interface Start<T : Closeable> {
        fun start(): Single<T>
    }

    interface ClientTransportAcceptor {
        fun transport(transport: () -> ClientTransport): Start<RSocket>

        fun transport(transport: ClientTransport): Start<RSocket> =
                transport { transport }

    }

    interface ServerTransportAcceptor {
        fun <T : Closeable> transport(transport: () -> ServerTransport<T>): Start<T>

        fun <T : Closeable> transport(transport: ServerTransport<T>): Start<T> =
                transport { transport }
    }

    private const val defaultStreamRequestLimit = 128

    private val emptyRSocket = object : AbstractRSocket() {}
}

typealias ClientAcceptor = () -> (RSocket) -> RSocket

typealias ServerAcceptor = () -> (Setup, RSocket) -> Single<RSocket>
