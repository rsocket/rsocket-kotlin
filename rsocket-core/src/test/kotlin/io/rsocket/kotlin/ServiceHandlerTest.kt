/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import io.netty.buffer.Unpooled
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ServiceHandlerTest {
    lateinit var sender: UnicastProcessor<Frame>
    lateinit var receiver: UnicastProcessor<Frame>
    lateinit var conn: LocalDuplexConnection
    private lateinit var errors: Errors
    private lateinit var keepAlive: KeepAliveOptions

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
        ClientServiceHandler(conn, keepAlive, keepAlive.keepAliveData(), errors)
        val sentKeepAlives = sender.take(3).toList().blockingGet()
        for (frame in sentKeepAlives) {
            assertTrue(frame.type == FrameType.KEEPALIVE)
            assertTrue(Frame.Keepalive.hasRespondFlag(frame))
        }
    }

    @Test(timeout = 2_000)
    fun clientServiceHandlerKeepAliveTimeout() {
        ClientServiceHandler(conn, keepAlive, keepAlive.keepAliveData(), errors)
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
    fun clientKeepAliveRespond() {
        val expectedReceive = "receive"
        val keepAlive = KeepAliveOptions()
                .keepAliveInterval(Duration.ofSeconds(42))
        ClientServiceHandler(conn, keepAlive, keepAlive.keepAliveData(), errors)

        receiver.onNext(Frame.Keepalive.from(Unpooled.wrappedBuffer(expectedReceive.bytes()), true))

        val sent = sender
                .filter { it.type == FrameType.KEEPALIVE }
                .firstOrError()
                .blockingGet()

        assertFalse(Frame.Keepalive.hasRespondFlag(sent))
        assertEquals(expectedReceive, sent.dataUtf8)
        assertTrue(errors.get().isEmpty())
    }

    @Test
    fun clientKeepAliveDataProducer() {
        val expectedSent = "test"
        val kad = TestKeepAliveData(expectedSent)
        val keepAlive = KeepAliveOptions()
                .keepAliveData(kad)
        ClientServiceHandler(conn, keepAlive, keepAlive.keepAliveData(), errors)
        val sent = sender
                .filter { it.type == FrameType.KEEPALIVE }
                .take(2)
                .toList()
                .blockingGet()
        sent.forEach {
            val actualData = it.dataUtf8
            assertEquals(expectedSent, actualData)
        }
        assertTrue(errors.get().isEmpty())
    }

    @Test
    fun clientKeepAliveDataHandler() {
        val expectedReceived = "receive"
        val kad = TestKeepAliveData("test")
        val keepAlive = KeepAliveOptions()
                .keepAliveData(kad)
        ClientServiceHandler(conn, keepAlive, keepAlive.keepAliveData(), errors)
        receiver.onNext(Frame.Keepalive.from(Unpooled.wrappedBuffer(expectedReceived.bytes()), false))
        receiver.onNext(Frame.Keepalive.from(Unpooled.wrappedBuffer(expectedReceived.bytes()), false))

        val actualReceive = kad.handled()
        assertEquals(2, actualReceive.size)
        actualReceive.forEach { actual ->
            assertEquals(expectedReceived, actual)
        }
        assertTrue(errors.get().isEmpty())
    }
}

private fun String.bytes() = toByteArray(StandardCharsets.UTF_8)

private fun ByteBuffer.asString() = StandardCharsets.UTF_8.decode(this).toString()

private class TestKeepAliveData(private val data: String) : KeepAliveData {

    private val handled = ArrayList<String>()

    override fun producer(): () -> ByteBuffer = {
        ByteBuffer.wrap(data.bytes())
    }

    override fun consumer(): (ByteBuffer) -> Unit = {
        handled += it.asString()
    }

    fun handled(): List<String> = ArrayList(handled)
}

private class Errors : (Throwable) -> Unit {
    private val errs = ArrayList<Throwable>()
    override fun invoke(err: Throwable) {
        errs += err
    }

    fun get() = errs
}