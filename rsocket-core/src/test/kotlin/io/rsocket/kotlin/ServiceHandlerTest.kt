package io.rsocket.kotlin

import io.netty.buffer.Unpooled
import io.netty.buffer.Unpooled.EMPTY_BUFFER
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.exceptions.ConnectionException
import io.rsocket.kotlin.exceptions.RejectedSetupException
import io.rsocket.kotlin.internal.ClientServiceHandler
import io.rsocket.kotlin.internal.ServerServiceHandler
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ServiceHandlerTest {
    lateinit var sender: UnicastProcessor<Frame>
    lateinit var receiver: UnicastProcessor<Frame>
    lateinit var conn: LocalDuplexConnection
    private lateinit var errors: Errors
    private lateinit var keepAlive: KeepAlive

    @Before
    fun setUp() {
        sender = UnicastProcessor.create<Frame>()
        receiver = UnicastProcessor.create<Frame>()
        conn = LocalDuplexConnection("clientRequesterConn", sender, receiver)
        errors = Errors()
        keepAlive = KeepAliveOptions()
    }

    @After
    fun tearDown() {
        conn.close().subscribe()
    }

    @Test
    fun serviceHandlerLease() {
        ServerServiceHandler(conn, keepAlive, errors)
        receiver.onNext(Frame.Lease.from(1000, 42, EMPTY_BUFFER))
        val errs = errors.get()
        assertEquals(1, errs.size)
        assertTrue(errs.first() is IllegalArgumentException)
    }

    @Test
    fun serviceHandlerError() {
        ServerServiceHandler(conn, keepAlive, errors)
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
        ServerServiceHandler(conn, keepAlive, errors)
        receiver.onNext(Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        val keepAliveResponse = sender.blockingFirst()
        assertTrue(keepAliveResponse.type == FrameType.KEEPALIVE)
        assertFalse(Frame.Keepalive.hasRespondFlag(keepAliveResponse))
    }

    @Test(timeout = 2_000)
    fun serverServiceHandlerKeepAliveTimeout() {
        ServerServiceHandler(conn, keepAlive, errors)
        conn.onClose().blockingAwait()
        val errs = errors.get()
        assertEquals(1, errs.size)
        val err = errs.first()
        assertTrue(err is ConnectionException)
        assertTrue((err as ConnectionException).message
                ?.startsWith("keep-alive timed out")
                ?: throw AssertionError(
                        "ConnectionException error must be non-null"))
    }

    @Test(timeout = 2_000)
    fun clientServiceHandlerKeepAlive() {
        ClientServiceHandler(
                conn,
                KeepAliveOptions(),
                errors)
        val sentKeepAlives = sender.take(3).toList().blockingGet()
        for (frame in sentKeepAlives) {
            assertTrue(frame.type == FrameType.KEEPALIVE)
            assertTrue(Frame.Keepalive.hasRespondFlag(frame))
        }
    }

    @Test(timeout = 2_000)
    fun clientServiceHandlerKeepAliveTimeout() {
        ClientServiceHandler(conn, keepAlive, errors)
        conn.onClose().blockingAwait()
        val errs = errors.get()
        assertEquals(1, errs.size)
        val err = errs.first()
        assertTrue(err is ConnectionException)
        assertTrue((err as ConnectionException).message
                ?.startsWith("keep-alive timed out")
                ?: throw AssertionError(
                        "ConnectionException error must be non-null"))
    }
}

private class Errors : (Throwable) -> Unit {
    private val errs = ArrayList<Throwable>()
    override fun invoke(err: Throwable) {
        errs += err
    }

    fun get() = errs
}