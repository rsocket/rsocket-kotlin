package io.rsocket.android

import io.netty.buffer.Unpooled
import io.netty.buffer.Unpooled.EMPTY_BUFFER
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.exceptions.RejectedSetupException
import io.rsocket.android.internal.ClientServiceHandler
import io.rsocket.android.internal.KeepAliveInfo
import io.rsocket.android.internal.ServerServiceHandler
import io.rsocket.android.test.util.LocalDuplexConnection
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ServiceConnectionHandlerTest {
    lateinit var sender: UnicastProcessor<Frame>
    lateinit var receiver: UnicastProcessor<Frame>
    lateinit var conn: LocalDuplexConnection

    @Before
    fun setUp() {
        sender = UnicastProcessor.create<Frame>()
        receiver = UnicastProcessor.create<Frame>()
        conn = LocalDuplexConnection("clientRequesterConn", sender, receiver)
    }

    @After
    fun tearDown() {
        conn.close().subscribe()
    }

    @Test
    fun serviceHandlerLease() {
        val errors = Errors()
        ServerServiceHandler(conn, errors)
        receiver.onNext(Frame.Lease.from(1000, 42, EMPTY_BUFFER))
        val errs = errors.get()
        assertEquals(1, errs.size)
        assertTrue(errs.first() is IllegalArgumentException)
    }

    @Test
    fun serviceHandlerError() {
        val errors = Errors()
        ServerServiceHandler(conn, errors)
        receiver.onNext(Frame.Error.from(0, RejectedSetupException("error")))
        val errs = errors.get()
        assertEquals(1, errs.size)
        assertTrue(errs.first() is RejectedSetupException)
        val succ = conn.onClose().blockingAwait(2, TimeUnit.SECONDS)
        if (!succ) {
            throw IllegalStateException("Error frame on stream 0 did not close connection")
        }
    }

    @Test(timeout = 2_000)
    fun serverServiceHandlerKeepAlive() {
        val errors = Errors()
        ServerServiceHandler(conn, errors)
        receiver.onNext(Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        val keepAliveResponse = sender.blockingFirst()
        assertTrue(keepAliveResponse.type == FrameType.KEEPALIVE)
        assertFalse(Frame.Keepalive.hasRespondFlag(keepAliveResponse))
    }

    @Test(timeout = 2_000)
    fun clientServiceHandlerKeepAlive() {
        val errors = Errors()
        ClientServiceHandler(
                conn,
                errors,
                KeepAliveInfo(
                        Duration.ofMillis(100),
                        Duration.ofSeconds(1),
                        3))
        val sentKeepAlives = sender.take(3).toList().blockingGet()
        for (frame in sentKeepAlives) {
            assertTrue(frame.type == FrameType.KEEPALIVE)
            assertTrue(Frame.Keepalive.hasRespondFlag(frame))
        }
    }
}

private class Errors : (Throwable) -> Unit {
    private val errs = ArrayList<Throwable>()
    override fun invoke(err: Throwable) {
        errs += err
    }

    fun get() = errs
}