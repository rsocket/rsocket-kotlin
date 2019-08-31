/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin

import io.reactivex.Completable
import io.reactivex.Single
import io.rsocket.kotlin.interceptors.GlobalInterceptors
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.fragmentation.FragmentationInterceptor
import io.rsocket.kotlin.transport.ClientTransport
import io.rsocket.kotlin.transport.ServerTransport
import io.rsocket.kotlin.util.AbstractRSocket

/** Factory for creating RSocket clients and servers  */
object RSocketFactory {

    /**
     * @return factory that creates RSockets clients connecting to RSocket servers
     */
    fun connect(): ClientRSocketFactory = ClientRSocketFactory()

    /**
     * @return factory that creates RSocket servers accepting connections
     * from RSocket clients
     */
    fun receive(): ServerRSocketFactory = ServerRSocketFactory()

    class ClientRSocketFactory {

        private var acceptor: ClientAcceptor = { { emptyRSocket } }
        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val interceptors = GlobalInterceptors.create()
        private var flags = 0
        private var setupPayload: Payload = DefaultPayload.EMPTY
        private val keepAlive = KeepAliveOptions()
        private val mediaType = MediaTypeOptions()
        private val options = RSocketOptions()

        /**
         * Configure connection and RSocket interceptors
         *
         * @param configure function which configures RSocket [InterceptorOptions]
         * @return this ClientRSocketFactory
         */
        fun interceptors(configure: (InterceptorOptions) -> Unit): ClientRSocketFactory {
            configure(interceptors)
            return this
        }

        /**
         * Configure keep-alive options
         *
         * @param configure function which configures RSocket [KeepAliveOptions]
         * @return this ClientRSocketFactory
         */
        fun keepAlive(configure: (KeepAliveOptions) -> Unit): ClientRSocketFactory {
            configure(keepAlive)
            return this
        }

        /**
         * Configure data and metadata payloads MIME type of RSocket
         *
         * @param configure function which configures RSocket [MediaTypeOptions]
         * @return this ClientRSocketFactory
         */
        fun mimeType(configure: (MediaTypeOptions) -> Unit)
                : ClientRSocketFactory {
            configure(mediaType)
            return this
        }

        /**
         * Configure frame fragmentation and reassembly of requester RSocket
         *
         * @param mtu sets payload length threshold for applying fragmentation.
         *            0 value means fragmentation is disabled. Must be non-negative
         * @return this ClientRSocketFactory
         */
        fun fragment(mtu: Int): ClientRSocketFactory {
            assertFragmentation(mtu)
            this.mtu = mtu
            return this
        }

        /**
         * Sets consumer for errors which are out of scope of RSocket interaction
         * requests (e.g. request-response, request-stream and so on)
         *
         * @param errorConsumer function which handles errors
         * @return this ClientRSocketFactory
         */
        fun errorConsumer(errorConsumer: (Throwable) -> Unit): ClientRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        /**
         * Sets Setup frame payload of RSocket
         *
         * @param payload Setup frame payload of RSocket
         * @return this ClientRSocketFactory
         */
        fun setupPayload(payload: Payload): ClientRSocketFactory {
            this.setupPayload = payload
            return this
        }

        /**
         * Configure auxiliary capabilities of RSocket interactions (request-response,
         * request-stream and so on)
         *
         * @param configure function which configures  [RSocketOptions]
         * @return this ClientRSocketFactory
         */
        fun options(configure: (RSocketOptions) -> Unit): ClientRSocketFactory {
            configure(options)
            return this
        }

        /**
         * Sets transport of client RSocket
         *
         * @param transport supplier used to create [ClientTransport] of RSocket
         * @return this ClientRSocketFactory
         */
        fun transport(transport: () -> ClientTransport): Start<RSocket> =
                clientStart(acceptor, transport)

        /**
         * Sets transport of client RSocket
         *
         * @param transport [ClientTransport] of RSocket
         * @return this ClientRSocketFactory
         */
        fun transport(transport: ClientTransport): Start<RSocket> =
                transport { transport }

        /**
         * Sets requests acceptor of client RSocket
         *
         * @param acceptor handler of client RSocket which accepts requests
         * from peer
         * @return this ClientRSocketFactory
         */
        fun acceptor(acceptor: ClientAcceptor): ClientTransportAcceptor {
            this.acceptor = acceptor
            return object : ClientTransportAcceptor {
                override fun transport(transport: () -> ClientTransport)
                        : Start<RSocket> = clientStart(acceptor, transport)
            }
        }

        private fun clientStart(acceptor: ClientAcceptor,
                                transport: () -> ClientTransport): ClientStart =

                ClientStart(acceptor,
                        errorConsumer,
                        mtu,
                        flags,
                        setupPayload,
                        keepAlive.copy(),
                        mediaType.copy(),
                        options.copy(),
                        transport,
                        interceptors.copy())

        private class ClientStart(
                private val acceptor: ClientAcceptor,
                private val errorConsumer: (Throwable) -> Unit,
                private var mtu: Int,
                private val flags: Int,
                private val setupPayload: Payload,
                keepAliveOpts: KeepAliveOptions,
                private val mediaType: MediaType,
                options: RSocketOptions,
                private val transportClient: () -> ClientTransport,
                private val parentInterceptors: InterceptorRegistry)
            : Start<RSocket> {

            private val streamRequestLimit = options.streamRequestLimit()
            private val keepALive = keepAliveOpts as KeepAlive
            private val keepAliveData = keepAliveOpts.keepAliveData()

            override fun start(): Single<RSocket> {
                return transportClient()
                        .connect()
                        .flatMap { connection ->
                            val enabledFragmentation =
                                    enableFragmentation(parentInterceptors)
                            val interceptConnection =
                                    enabledFragmentation as InterceptConnection
                            val interceptRSocket =
                                    enabledFragmentation as InterceptRSocket

                            val demuxer = ClientConnectionDemuxer(
                                    connection,
                                    interceptConnection)

                            val requesterRSocket = RSocketRequester(
                                    demuxer.requesterConnection(),
                                    errorConsumer,
                                    ClientStreamIds(),
                                    streamRequestLimit)

                            val wrappedRequesterRSocket = interceptRSocket
                                    .interceptRequester(requesterRSocket)

                            val acceptorRSocket = acceptor()(wrappedRequesterRSocket)

                            val wrappedAcceptorRSocket = interceptRSocket
                                    .interceptAcceptor(acceptorRSocket)

                            RSocketResponder(
                                    demuxer.responderConnection(),
                                    wrappedAcceptorRSocket,
                                    errorConsumer,
                                    streamRequestLimit)

                            ClientServiceHandler(
                                    demuxer.serviceConnection(),
                                    keepALive,
                                    keepAliveData,
                                    errorConsumer)

                            val setupFrame = createSetupFrame()

                            connection
                                    .sendOne(setupFrame)
                                    .andThen(Single.just(wrappedRequesterRSocket))
                        }
            }

            private fun enableFragmentation(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry {
                parentInterceptors.connectionFirst(
                        FragmentationInterceptor(mtu))
                return parentInterceptors
            }

            private fun createSetupFrame(): Frame {
                return Frame.Setup.from(
                        flags,
                        keepALive.keepAliveInterval().intMillis,
                        keepALive.keepAliveMaxLifeTime().intMillis,
                        mediaType.metadataMimeType(),
                        mediaType.dataMimeType(),
                        setupPayload)
            }
        }
    }

    class ServerRSocketFactory internal constructor() {

        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private val interceptors = GlobalInterceptors.create()
        private val options = RSocketOptions()

        /**
         * Configure connection and RSocket interceptors
         *
         * @param configure function which configures RSocket [InterceptorOptions]
         * @return this ServerRSocketFactory
         */
        fun interceptors(configure: (InterceptorOptions) -> Unit): ServerRSocketFactory {
            configure(interceptors)
            return this
        }

        /**
         * Configure frame fragmentation and reassembly of requester RSocket
         *
         * @param mtu sets payload length threshold for applying fragmentation.
         *            0 value means fragmentation is disabled. Must be non-negative
         * @return this ServerRSocketFactory
         */
        fun fragment(mtu: Int): ServerRSocketFactory {
            assertFragmentation(mtu)
            this.mtu = mtu
            return this
        }

        /**
         * Sets consumer for errors which are out of scope of RSocket interaction
         * requests (e.g. request-response, request-stream and so on)
         *
         * @param errorConsumer function which handles errors
         * @return this ServerRSocketFactory
         */
        fun errorConsumer(errorConsumer: (Throwable) -> Unit): ServerRSocketFactory {
            this.errorConsumer = errorConsumer
            return this
        }

        /**
         * Configure auxilary capabilities of RSocket interactions (request-response,
         * request-stream and so on)
         *
         * @param configure function which configures  [RSocketOptions]
         * @return this ServerRSocketFactory
         */
        fun options(configure: (RSocketOptions) -> Unit): ServerRSocketFactory {
            configure(options)
            return this
        }

        /**
         * Sets requests acceptor of client RSocket
         *
         * @param acceptor handler of server RSocket which accepts requests
         * from peer
         * @return [ServerTransportAcceptor] which accepts server RSocket transport
         */
        fun acceptor(acceptor: ServerAcceptor): ServerTransportAcceptor {
            return object : ServerTransportAcceptor {

                override fun <T : Closeable> transport(
                        transport: () -> ServerTransport<T>): Start<T> =
                        ServerStart(transport,
                                acceptor,
                                errorConsumer,
                                mtu,
                                interceptors.copy(),
                                options.copy())
            }
        }

        private class ServerStart<T : Closeable>(
                private val transportServer: () -> ServerTransport<T>,
                private val acceptor: ServerAcceptor,
                private val errorConsumer: (Throwable) -> Unit,
                private val mtu: Int,
                private val parentInterceptors: InterceptorRegistry,
                options: RSocketOptions) : Start<T> {

            private val streamRequestLimit = options.streamRequestLimit()

            override fun start(): Single<T> {
                return transportServer().start(object
                    : ServerTransport.ConnectionAcceptor {

                    override fun invoke(duplexConnection: DuplexConnection)
                            : Completable {

                        val enabledServerContract =
                                enableServerContract(parentInterceptors)
                        val enabledFragmentation =
                                enableFragmentation(enabledServerContract)
                        val interceptConnection =
                                enabledFragmentation as InterceptConnection
                        val interceptRSocket =
                                enabledFragmentation as InterceptRSocket

                        val demuxer = ServerConnectionDemuxer(
                                duplexConnection,
                                interceptConnection)

                        return demuxer
                                .setupConnection()
                                .receive()
                                .firstOrError()
                                .flatMapCompletable { setup ->
                                    accept(setup,
                                            interceptRSocket,
                                            demuxer)
                                }
                    }
                })
            }

            private fun accept(setupFrame: Frame,
                               interceptors: InterceptRSocket,
                               demuxer: ConnectionDemuxer): Completable {

                val setup = SetupImpl.create(setupFrame)

                val requesterRSocket = RSocketRequester(
                        demuxer.requesterConnection(),
                        errorConsumer,
                        ServerStreamIds(),
                        streamRequestLimit)

                val wrappedRequesterRSocket =
                        interceptors.interceptRequester(requesterRSocket)

                ServerServiceHandler(
                        demuxer.serviceConnection(),
                        setup,
                        errorConsumer)

                val acceptorRSocket = acceptor()(setup, wrappedRequesterRSocket)

                val rejectingAcceptorRSocket =
                        RSocketRejectionHandler(demuxer.requesterConnection())
                                .rejectOnError(acceptorRSocket)

                return rejectingAcceptorRSocket
                        .map { acceptor -> interceptors.interceptAcceptor(acceptor) }
                        .doOnSuccess { acceptor ->
                            RSocketResponder(
                                    demuxer.responderConnection(),
                                    acceptor,
                                    errorConsumer,
                                    streamRequestLimit)
                        }
                        .ignoreElement()
            }

            private fun enableServerContract(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry {

                parentInterceptors.connectionFirst(
                        ServerContractInterceptor(errorConsumer, false))
                return parentInterceptors
            }

            private fun enableFragmentation(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry {
                parentInterceptors.connectionFirst(
                        FragmentationInterceptor(mtu))
                return parentInterceptors
            }
        }
    }

    private fun assertFragmentation(mtu: Int) {
        if (mtu < 0) {
            throw IllegalArgumentException(
                    "fragmentation mtu must be non-negative")
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

    private val emptyRSocket = object : AbstractRSocket() {}
}

/**
 * Factory which creates requests acceptor of client RSocket. Takes requester
 * RSocket and returns responder (handler) RSocket
 */
typealias ClientAcceptor = () -> (RSocket) -> RSocket

/**
 * Factory which creates requests acceptor of server RSocket. Takes setup payload
 * and requester RSocket and returns responder (handler) RSocket
 */
typealias ServerAcceptor = () -> (Setup, RSocket) -> Single<RSocket>
