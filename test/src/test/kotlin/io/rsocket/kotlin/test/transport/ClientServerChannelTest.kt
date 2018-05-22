package io.rsocket.kotlin.test.transport

import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketFactory
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.NettyContextCloseable
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ClientServerChannelTest {
    private lateinit var server: NettyContextCloseable
    private lateinit var client: RSocket
    private lateinit var channelHandler: ChannelHandler
    @Before
    fun setUp() {
        val address = InetSocketAddress
                .createUnresolved("localhost", 0)
        val serverTransport = TcpServerTransport.create(address)
        channelHandler = ChannelHandler(intervalMillis)
        server = RSocketFactory
                .receive()
                .acceptor { { _, _ -> Single.just(channelHandler) } }
                .transport(serverTransport)
                .start()
                .blockingGet()

        val clientTransport = TcpClientTransport
                .create(server.address())

        client = RSocketFactory
                .connect()
                .transport { clientTransport }
                .start()
                .blockingGet()
    }

    @Test
    fun channel() {
        var requestsCount = 0
        client.requestChannel(textStream(intervalMillis))
                .subscribe({ }, { throw it })

        val delay = Flowable
                .timer(5, TimeUnit.SECONDS)
                .share()

        Flowable.interval(250, TimeUnit.MILLISECONDS)
                .takeUntil(delay)
                .subscribe {
                    val cur = channelHandler.counter()
                    if (requestsCount == cur) {
                        throw RuntimeException("Channel stream does not advance: $cur")
                    }
                    requestsCount = cur
                }
        delay.ignoreElements().blockingAwait()
    }

    internal class ChannelHandler(private val intervalMillis: Long)
        : AbstractRSocket() {
        private val counter = AtomicInteger()

        fun counter() = counter.get()

        override fun requestChannel(payloads: Publisher<Payload>):
                Flowable<Payload> {
            Flowable.fromPublisher(payloads)
                    .subscribe(
                            { counter.incrementAndGet() },
                            { println("Server channel error: $it") })
            return textStream(intervalMillis)
        }

    }

    companion object {
        internal fun textStream(intervalMillis: Long) =
                Flowable.interval(intervalMillis, TimeUnit.MICROSECONDS)
                        .onBackpressureDrop()
                        .map { DefaultPayload.textPayload(it.toString()) }

        internal const val intervalMillis: Long = 100
    }
}