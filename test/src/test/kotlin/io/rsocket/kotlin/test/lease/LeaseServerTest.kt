package io.rsocket.kotlin.test.lease

import io.reactivex.Flowable
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
    private lateinit var serverLease: LeaseSupp
    private lateinit var clientSocket: RSocket
    private lateinit var leaseGranter: LeaseGranter
    @Before
    fun setUp() {
        serverLease = LeaseSupp()
        nettyContextCloseable = RSocketFactory.receive()
                .lease { opts -> opts.leaseSupport(serverLease) }
                .acceptor {
                    { _, _ ->
                        Single.just(
                                object : AbstractRSocket() {
                                    override fun requestStream(payload: Payload)
                                            : Flowable<Payload> =
                                            Flowable.just(payload)
                                })
                    }
                }
                .transport(TcpServerTransport.create("localhost", 0))
                .start()
                .blockingGet()

        val address = nettyContextCloseable.address()
        clientSocket = RSocketFactory
                .connect()
                .lease { opts -> opts.leaseSupport(LeaseSupp()) }
                .keepAlive { opts ->
                    opts.keepAliveInterval(Duration.ofMinutes(1))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(20))
                }
                .transport(TcpClientTransport.create(address))
                .start()
                .blockingGet()

        leaseGranter = serverLease.leaseGranter().blockingGet()
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
        leaseGranter.grantLease(2, 10_000)
                .delay(100, TimeUnit.MILLISECONDS)
                .blockingAwait()
        assertEquals(clientSocket.availability(), 1.0, 1e-5)
        clientSocket.requestStream(payload())
                .blockingSubscribe()
        assertEquals(clientSocket.availability(), 0.5, 1e-5)
        clientSocket.requestStream(payload())
                .blockingSubscribe()
        assertEquals(clientSocket.availability(), 0.0, 1e-5)

        val subscriber = TestSubscriber<Payload>()
        clientSocket.requestStream(payload())
                .blockingSubscribe(subscriber)
        assertEquals(1, subscriber.errorCount())
        assertTrue(subscriber.errors().first() is MissingLeaseException)
        leaseGranter.grantLease(1, 10_000)
                .delay(100, TimeUnit.MILLISECONDS)
                .blockingAwait()
        assertEquals(1.0, clientSocket.availability(), 1e-5)
    }

    @Test
    fun grantLeaseTtl() {
        leaseGranter.grantLease(2, 200)
                .delay(250, TimeUnit.MILLISECONDS)
                .blockingAwait()

        assertEquals(clientSocket.availability(), 0.0, 1e-5)
        val subscriber = TestSubscriber<Payload>()
        clientSocket.requestStream(payload())
                .blockingSubscribe(subscriber)

        assertEquals(1, subscriber.errorCount())
        assertTrue(subscriber.errors().first() is MissingLeaseException)
    }

    private fun payload() = DefaultPayload("data")

    private class LeaseSupp : (LeaseSupport) -> Unit {
        private val leaseRefs = BehaviorProcessor.create<LeaseGranter>()

        override fun invoke(leaseSupport: LeaseSupport) {
            leaseRefs.onNext(leaseSupport.granter())
        }

        fun leaseGranter(): Single<LeaseGranter> = leaseRefs.firstOrError()
    }

}