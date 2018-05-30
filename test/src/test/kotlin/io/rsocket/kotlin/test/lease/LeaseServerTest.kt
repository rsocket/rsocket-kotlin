package io.rsocket.kotlin.test.lease

import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.kotlin.*
import io.rsocket.kotlin.exceptions.MissingLeaseException
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.NettyContextCloseable
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LeaseServerTest {
    private lateinit var nettyContextCloseable: NettyContextCloseable
    private lateinit var serverLease: LeaseRefs
    private lateinit var clientSocket: RSocket
    private lateinit var leaseRef: LeaseRef
    @Before
    fun setUp() {
        serverLease = LeaseRefs()
        nettyContextCloseable = RSocketFactory.receive()
                .enableLease(serverLease)
                .acceptor {
                    { _, _ ->
                        Single.just(
                                object : AbstractRSocket() {
                                    override fun requestResponse(payload: Payload)
                                            : Single<Payload> =
                                            Single.just(payload)
                                })
                    }
                }
                .transport(TcpServerTransport.create("localhost", 0))
                .start()
                .blockingGet()

        val address = nettyContextCloseable.address()
        clientSocket = RSocketFactory
                .connect()
                .enableLease(LeaseRefs())
                .keepAlive { opts ->
                    opts.keepAliveInterval(Duration.ofMinutes(1))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(20))
                }
                .transport(TcpClientTransport.create(address))
                .start()
                .blockingGet()

        leaseRef = serverLease.leaseRef().blockingGet()
    }

    @After
    fun tearDown() {
        clientSocket.close().subscribe()
        nettyContextCloseable.close().subscribe()
        nettyContextCloseable.onClose().blockingAwait()
    }

    @Test
    fun grantLeaseNumberOfRequests() {
        assertEquals(clientSocket.availability(), 0.0, 1e-5)
        leaseRef.grantLease(2, 10_000)
                .delay(100, TimeUnit.MILLISECONDS)
                .blockingAwait()
        assertEquals(clientSocket.availability(), 1.0, 1e-5)
        clientSocket.requestResponse(payload())
                .blockingGet()
        assertEquals(clientSocket.availability(), 0.5, 1e-5)
        clientSocket.requestResponse(payload())
                .blockingGet()
        assertEquals(clientSocket.availability(), 0.0, 1e-5)

        val subscriber = TestSubscriber<Payload>()
        clientSocket.requestResponse(payload())
                .toFlowable()
                .blockingSubscribe(subscriber)
        assertEquals(1, subscriber.errorCount())
        assertTrue(subscriber.errors().first() is MissingLeaseException)
        leaseRef.grantLease(1, 10_000)
                .delay(100, TimeUnit.MILLISECONDS)
                .blockingAwait()
        assertEquals(1.0, clientSocket.availability(), 1e-5)
    }

    @Test
    fun grantLeaseTtl() {
        leaseRef.grantLease(2, 200)
                .delay(250, TimeUnit.MILLISECONDS)
                .blockingAwait()

        assertEquals(clientSocket.availability(), 0.0, 1e-5)
        val subscriber = TestSubscriber<Payload>()
        clientSocket.requestResponse(payload()).toFlowable()
                .blockingSubscribe(subscriber)

        assertEquals(1, subscriber.errorCount())
        assertTrue(subscriber.errors().first() is MissingLeaseException)
    }

    private fun payload() = DefaultPayload("data")

    private class LeaseRefs : (LeaseRef) -> Unit {
        private val leaseRefs = BehaviorProcessor.create<LeaseRef>()

        fun leaseRef(): Single<LeaseRef> = leaseRefs.firstOrError()

        override fun invoke(leaseRef: LeaseRef) {
            leaseRefs.onNext(leaseRef)
        }
    }

}