package io.rsocket.kotlin.internal.lease

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class LeaseConnectionTest {

    private lateinit var leaseGranterConnection: LeaseConnection
    private lateinit var sender: PublishProcessor<Frame>
    private lateinit var receiver: PublishProcessor<Frame>
    private lateinit var send: LeaseManager
    private lateinit var receive: LeaseManager

    @Before
    fun setUp() {
        val leaseContext = LeaseContext()
        send = LeaseManager("send")
        receive = LeaseManager("receive")
        sender = PublishProcessor.create()
        receiver = PublishProcessor.create()
        val local = LocalDuplexConnection("test", sender, receiver)
        leaseGranterConnection = LeaseConnection(
                leaseContext,
                local,
                send,
                receive)
    }

    @Test
    fun sentLease() {
        leaseGranterConnection.send(
                Flowable.just(Frame.Lease.from(2_000, 1, Unpooled.EMPTY_BUFFER)))
                .blockingAwait()
        assertEquals(1.0, receive.availability(), 1e-5)
        assertEquals(0.0, send.availability(), 1e-5)
    }

    @Test
    fun receivedLease() {
        leaseGranterConnection.receive().subscribe()
        receiver.onNext(Frame.Lease.from(2, 1, Unpooled.EMPTY_BUFFER))
        assertEquals(0.0, receive.availability(), 1e-5)
        assertEquals(1.0, send.availability(), 1e-5)
    }

    @Test
    fun grantLease() {
        Completable.timer(100, TimeUnit.MILLISECONDS)
                .andThen(Completable.defer {
                    leaseGranterConnection
                            .grantLease(2, 1, ByteBuffer.allocateDirect(0))
                })
                .subscribe()
        val f = sender.firstOrError().blockingGet()

        assertNotNull(f)
        assertTrue(f.type === FrameType.LEASE)
        assertEquals(2, Frame.Lease.numberOfRequests(f))
        assertEquals(1, Frame.Lease.ttl(f))
    }
}
