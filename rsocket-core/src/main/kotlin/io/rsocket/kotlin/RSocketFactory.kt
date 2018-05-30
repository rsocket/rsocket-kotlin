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

package io.rsocket.kotlin

import io.reactivex.Completable
import io.reactivex.Single
import io.rsocket.kotlin.interceptors.GlobalInterceptors
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.fragmentation.FragmentationInterceptor
import io.rsocket.kotlin.internal.lease.ClientLeaseSupport
import io.rsocket.kotlin.internal.lease.ServerLeaseSupport
import io.rsocket.kotlin.transport.ClientTransport
import io.rsocket.kotlin.transport.ServerTransport
import io.rsocket.kotlin.util.AbstractRSocket

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
        private var leaseRefConsumer: ((LeaseRef) -> Unit)? = null
        private val interceptors = GlobalInterceptors.create()
        private var flags = 0
        private var setupPayload: Payload = DefaultPayload.EMPTY
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

        fun enableLease(leaseRefConsumer: (LeaseRef) -> Unit): ClientRSocketFactory {
            this.flags = Frame.Setup.enableLease(flags)
            this.leaseRefConsumer = leaseRefConsumer
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
                clientStart(acceptor, transport)

        fun transport(transport: ClientTransport): Start<RSocket> =
                transport { transport }

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
                        leaseRefConsumer,
                        flags,
                        setupPayload,
                        keepAlive.copy(),
                        mediaType.copy(),
                        streamRequestLimit,
                        transport,
                        interceptors.copy())

        private class ClientStart(
                private val acceptor: ClientAcceptor,
                private val errorConsumer: (Throwable) -> Unit,
                private var mtu: Int,
                private val leaseRef: ((LeaseRef) -> Unit)?,
                private val flags: Int,
                private val setupPayload: Payload,
                private val keepAlive: KeepAlive,
                private val mediaType: MediaType,
                private val streamRequestLimit: Int,
                private val transportClient: () -> ClientTransport,
                private val parentInterceptors: InterceptorRegistry)
            : Start<RSocket> {

            override fun start(): Single<RSocket> {
                return transportClient()
                        .connect()
                        .flatMap { connection ->

                            val withLease =
                                    enableLease(parentInterceptors)

                            val interceptors =
                                    enableFragmentation(withLease)

                            val interceptConnection = interceptors as InterceptConnection
                            val interceptRSocket = interceptors as InterceptRSocket

                            val demuxer = ClientConnectionDemuxer(
                                    connection,
                                    interceptConnection)

                            val rSocketRequester = RSocketRequester(
                                    demuxer.requesterConnection(),
                                    errorConsumer,
                                    ClientStreamIds(),
                                    streamRequestLimit)

                            val wrappedRequester = interceptRSocket
                                    .interceptRequester(rSocketRequester)

                            val handlerRSocket = acceptor()(wrappedRequester)

                            val wrappedHandler = interceptRSocket
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

                            val setupFrame = createSetupFrame()

                            connection
                                    .sendOne(setupFrame)
                                    .andThen(Single.just(wrappedRequester))
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
                        keepAlive.keepAliveInterval().intMillis,
                        keepAlive.keepAliveMaxLifeTime().intMillis,
                        mediaType.metadataMimeType(),
                        mediaType.dataMimeType(),
                        setupPayload)
            }

            private fun enableLease(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry =
                    if (leaseRef != null) {
                        parentInterceptors.copyWith(
                                ClientLeaseSupport.enable(leaseRef)())
                    } else {
                        parentInterceptors.copy()
                    }
        }
    }

    class ServerRSocketFactory internal constructor() {

        private var errorConsumer: (Throwable) -> Unit = { it.printStackTrace() }
        private var mtu = 0
        private var leaseRefConsumer: ((LeaseRef) -> Unit)? = null
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

        fun enableLease(leaseRefConsumer: (LeaseRef) -> Unit): ServerRSocketFactory {
            this.leaseRefConsumer = leaseRefConsumer
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
            return object : ServerTransportAcceptor {

                override fun <T : Closeable> transport(
                        transport: () -> ServerTransport<T>): Start<T> =
                        ServerStart(transport,
                                acceptor,
                                errorConsumer,
                                mtu,
                                leaseRefConsumer,
                                interceptors.copy(),
                                streamRequestLimit)
            }
        }

        private class ServerStart<T : Closeable>(
                private val transportServer: () -> ServerTransport<T>,
                private val acceptor: ServerAcceptor,
                private val errorConsumer: (Throwable) -> Unit,
                private val mtu: Int,
                private val leaseRef: ((LeaseRef) -> Unit)?,
                private val parentInterceptors: InterceptorRegistry,
                private val streamRequestLimit: Int) : Start<T> {

            override fun start(): Single<T> {
                return transportServer().start(object
                    : ServerTransport.ConnectionAcceptor {

                    override fun invoke(duplexConnection: DuplexConnection)
                            : Completable {

                        val withLease =
                                enableLease(parentInterceptors)

                        val withServerContract =
                                enableServerContract(withLease)

                        val interceptors =
                                enableFragmentation(withServerContract)

                        val demuxer = ServerConnectionDemuxer(
                                duplexConnection,
                                interceptors as InterceptConnection)

                        return demuxer
                                .setupConnection()
                                .receive()
                                .firstOrError()
                                .flatMapCompletable { setup ->
                                    accept(setup,
                                            interceptors as InterceptRSocket,
                                            demuxer)
                                }
                    }
                })
            }

            private fun accept(setupFrame: Frame,
                               interceptors: InterceptRSocket,
                               demuxer: ConnectionDemuxer): Completable {

                val setup = SetupContents.create(setupFrame)

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

            private fun enableLease(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry =
                    if (leaseRef != null) {
                        parentInterceptors.copyWith(
                                ServerLeaseSupport.enable(leaseRef)())
                    } else {
                        parentInterceptors.copy()
                    }

            private fun enableServerContract(parentInterceptors: InterceptorRegistry)
                    : InterceptorRegistry {

                parentInterceptors.connectionFirst(
                        ServerContractInterceptor(errorConsumer, leaseRef != null))
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
