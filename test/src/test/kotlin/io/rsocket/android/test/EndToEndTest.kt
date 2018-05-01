package io.rsocket.android.test

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.android.*
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.transport.ServerTransport
import io.rsocket.android.transport.netty.server.NettyContextCloseable
import io.rsocket.android.util.PayloadImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

abstract class EndToEndTest
(private val clientTransport: (InetSocketAddress) -> ClientTransport,
 private val serverTransport: (InetSocketAddress) -> ServerTransport<NettyContextCloseable>) {
    private lateinit var server: NettyContextCloseable
    private lateinit var client: RSocket
    private lateinit var handler: TestRSocketHandler
    private val errors = Errors()

    @Before
    fun setUp() {
        handler = TestRSocketHandler()
        val address = InetSocketAddress
                .createUnresolved("localhost", 0)

        server = RSocketFactory
                .receive()
                .errorConsumer(errors.errorsConsumer())
                .acceptor {
                    object : SocketAcceptor {
                        override fun accept(setup: ConnectionSetupPayload,
                                            sendingSocket: RSocket): Single<RSocket> {
                            return Single.just(handler)
                        }
                    }
                }.transport(serverTransport(address))
                .start()
                .blockingGet()

        client = RSocketFactory
                .connect()
                .errorConsumer(errors.errorsConsumer())
                .transport { clientTransport(server.address()) }
                .start()
                .blockingGet()
    }

    @After
    fun tearDown() {
        server.close()
                .andThen(server.onClose())
                .blockingAwait(10, TimeUnit.SECONDS)
    }

    @Test
    fun fireAndForget() {
        val data = testData()
        client.fireAndForget(data.payload())
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .blockingAwait(10, TimeUnit.SECONDS)
        assertThat(errors.errors()).isEmpty()
        assertThat(handler.fireAndForgetData()).hasSize(1)
                .contains(data)
    }

    @Test
    fun response() {
        val data = testData()
        val response = client.requestResponse(data.payload())
                .timeout(10, TimeUnit.SECONDS)
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(Data(response)).isEqualTo(data)
    }

    @Test
    fun stream() {
        val data = testData()
        val response = client.requestStream(data.payload())
                .timeout(10, TimeUnit.SECONDS)
                .map { Data(it) }
                .toList()
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(response).hasSize(1).contains(data)
    }

    @Test
    fun channel() {
        val data = testData()
        val response = client.requestChannel(Flowable.just(data.payload()))
                .timeout(10, TimeUnit.SECONDS)
                .toList()
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(response).hasSize(1)
        assertThat(Data(response[0])).isEqualTo(data)
    }

    @Test
    fun metadataPush() {
        val payload = PayloadImpl("", "md")
        client.metadataPush(payload)
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .timeout(10, TimeUnit.SECONDS)
                .blockingAwait()

        assertThat(errors.errors()).isEmpty()
        assertThat(handler.metadataPushData())
                .hasSize(1)
                .contains("md")
    }

    @Test
    open fun close() {
        val success = client.close()
                .andThen(client.onClose())
                .blockingAwait(10, TimeUnit.SECONDS)
        if (!success) {
            throw IllegalStateException("RSocket.close() did not trigger RSocket.onClose()")
        }
    }

    @Test
    fun availability() {
        assertThat(client.availability())
                .isCloseTo(1.0, Offset.offset(1e-5))
    }

    @Test
    open fun closedAvailability() {
        client.close()
                .andThen(client.onClose())
                .timeout(10, TimeUnit.SECONDS)
                .blockingAwait()

        assertThat(client.availability())
                .isCloseTo(0.0, Offset.offset(1e-5))
    }


    private fun testData() = Data("d", "md")

    internal class TestRSocketHandler : AbstractRSocket() {
        private val fnf = ArrayList<Data>()
        private val metadata = ArrayList<String>()

        override fun fireAndForget(payload: Payload): Completable {
            fnf += Data(payload)
            return Completable.complete()
        }

        override fun metadataPush(payload: Payload): Completable {
            metadata += payload.metadataUtf8
            return Completable.complete()
        }

        override fun requestResponse(payload: Payload): Single<Payload> {
            return Single.just(payload)
        }

        override fun requestStream(payload: Payload): Flowable<Payload> {
            return Flowable.just(payload)
        }

        override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
            return Flowable.fromPublisher(payloads)
        }

        fun fireAndForgetData() = fnf

        fun metadataPushData() = metadata

    }

    internal class Errors {

        private val errors = ArrayList<Throwable>()

        fun errorsConsumer(): (Throwable) -> Unit = {
            errors += (it)
        }

        fun errors() = errors
    }

    internal data class Data(val data: String, val metadata: String) {
        constructor(payload: Payload) : this(payload.dataUtf8, payload.metadataUtf8)

        fun payload(): Payload = PayloadImpl(data, metadata)
    }
}