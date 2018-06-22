package io.rsocket.kotlin.test.transport

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.ClientTransport
import io.rsocket.kotlin.transport.ServerTransport
import io.rsocket.kotlin.transport.netty.server.NettyContextCloseable
import io.rsocket.kotlin.util.AbstractRSocket
import io.rsocket.kotlin.DefaultPayload
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
    private lateinit var clientHandler: TestRSocketHandler
    private lateinit var serverHandler: TestRSocketHandler
    private val errors = Errors()

    @Before
    fun setUp() {
        val address = InetSocketAddress
                .createUnresolved("localhost", 0)
        val serverAcceptor = ServerAcceptor()
        clientHandler = TestRSocketHandler()
        server = RSocketFactory
                .receive()
                .errorConsumer(errors.errorsConsumer())
                .acceptor { serverAcceptor }
                .transport(serverTransport(address))
                .start()
                .blockingGet()

        client = RSocketFactory
                .connect()
                .errorConsumer(errors.errorsConsumer())
                .keepAlive {
                    it.keepAliveInterval(Duration.ofSeconds(42))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(42))
                }
                .acceptor { { clientHandler } }
                .transport { clientTransport(server.address()) }
                .start()
                .blockingGet()

        serverHandler = serverAcceptor.handler().blockingGet()
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
        assertThat(serverHandler.fireAndForgetData()).hasSize(1)
                .contains(data)
    }

    @Test
    open fun response() {
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
    fun clientMetadataPush() {
        val payload = DefaultPayload("", "md")
        client.metadataPush(payload)
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .timeout(10, TimeUnit.SECONDS)
                .blockingAwait()

        assertThat(errors.errors()).isEmpty()
        assertThat(serverHandler.metadataPushData())
                .hasSize(1)
                .contains("md")
    }

    @Test
    fun serverMetadataPush() {
        val payload = DefaultPayload("", "md")
        serverHandler.sendMetadataPush(payload)
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .timeout(10, TimeUnit.SECONDS)
                .blockingAwait()

        assertThat(errors.errors()).isEmpty()
        assertThat(clientHandler.metadataPushData())
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

    internal class TestRSocketHandler(private val requester: RSocket? = null) : AbstractRSocket() {
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

        fun sendMetadataPush(payload: Payload): Completable = requester
                ?.metadataPush(payload)
                ?: Completable.complete()

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

    internal class ServerAcceptor
        : (Setup, RSocket) -> Single<RSocket> {

        private val serverHandlerReady = BehaviorProcessor
                .create<TestRSocketHandler>()

        override fun invoke(setup: Setup,
                            sendingSocket: RSocket): Single<RSocket> {
            val handler = TestRSocketHandler(sendingSocket)
            serverHandlerReady.onNext(handler)
            return Single.just(handler)
        }

        fun handler(): Single<TestRSocketHandler> {
            return serverHandlerReady.firstOrError()
        }
    }

    internal data class Data(val data: String, val metadata: String) {
        constructor(payload: Payload) : this(payload.dataUtf8, payload.metadataUtf8)

        fun payload(): Payload = DefaultPayload(data, metadata)
    }
}