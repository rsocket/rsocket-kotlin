package io.rsocket.kotlin.internal

import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.exceptions.RejectedSetupException
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RejectingRSocketTest {
    private lateinit var sender: UnicastProcessor<Frame>
    private lateinit var receiver: UnicastProcessor<Frame>
    private lateinit var conn: LocalDuplexConnection

    @Before
    fun setUp() {
        sender = UnicastProcessor.create<Frame>()
        receiver = UnicastProcessor.create<Frame>()
        conn = LocalDuplexConnection("test", sender, receiver)
    }

    @Test(timeout = 2_000)
    fun rejectError() {
        val expectedMsg = "error"
        val rejectingRSocket = rejectingRSocket(IllegalArgumentException(expectedMsg))
        rejectingRSocket.subscribe({}, {})
        val frame = sender.firstOrError().blockingGet()
        assertTrue(frame.type == FrameType.ERROR)
        assertTrue(frame.streamId == 0)
        val err = Exceptions.from(frame)
        assertTrue(err is RejectedSetupException)
        assertEquals(expectedMsg, err.message)
    }

    @Test(timeout = 2_000)
    fun rejectErrorEmptyMessage() {
        val rejectingRSocket = rejectingRSocket(RuntimeException())
        rejectingRSocket.subscribe({}, {})
        val frame = sender.firstOrError().blockingGet()
        val err = Exceptions.from(frame)
        assertTrue(err is RejectedSetupException)
        assertEquals("", err.message)
    }

    private fun rejectingRSocket(err: Throwable): Single<RSocket> {
        val rSocket = Single.error<RSocket>(err)
        return RejectingRSocket(rSocket).with(conn)
    }
}