/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin.internal

import io.rsocket.kotlin.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

@OptIn(ExperimentalTime::class)
@InternalCoroutinesApi
class RSocketRequesterTest {
    private val connection = TestConnection()
    private val ignoredFrames = Channel<Frame>(Channel.UNLIMITED)
    private val requester = run {
        val state = RSocketStateImpl(connection, KeepAlive(1000.seconds, 1000.seconds), RequestStrategy.Default, ignoredFrames::offer)
        val requester = RSocketRequester(state, StreamId.client())
        state.start(RSocketRequestHandler { })
        requester
    }

    @Test
    fun testInvalidFrameOnStream0() = test {
        connection.sendToReceiver(RequestNFrame(0, 5))
        val frame = ignoredFrames.receive()
        assertTrue(frame is RequestNFrame)
    }

    @Test
    fun testStreamInitialN() = test {
        val flow = requester.requestStream(Payload.Empty).requesting(RequestStrategy(5))
        assertEquals(0, connection.sentFrames.size)
        flow.launchIn(CoroutineScope(connection.job))
        delay(100)
        assertEquals(1, connection.sentFrames.size)
        val frame = connection.receiveFromSender()
        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(5, frame.initialRequest)
    }

    @Test
    fun testHandleSetupException() = test {
        val errorMessage = "error"
        connection.sendToReceiver(ErrorFrame(0, RSocketError.Setup.Rejected(errorMessage)))
        delay(100)
        assertFalse(requester.isActive)
        val error = requester.job.getCancellationException().cause
        assertTrue(error is RSocketError.Setup.Rejected)
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun testHandleApplicationException() = test {
        val errorMessage = "error"
        val deferred = GlobalScope.async { requester.requestResponse(Payload.Empty) }
        delay(300)
        assertEquals(1, connection.sentFrames.size)
        val streamId = connection.sentFrames.first().streamId
        connection.sendToReceiver(ErrorFrame(streamId, RSocketError.ApplicationError(errorMessage)))
        assertFailsWith(RSocketError.ApplicationError::class, errorMessage) { deferred.await() }
    }

    @Test
    fun testHandleValidFrame() = test {
        val deferred = GlobalScope.async { requester.requestResponse(Payload.Empty) }
        delay(100)
        assertEquals(1, connection.sentFrames.size)
        val streamId = connection.sentFrames.first().streamId
        connection.sendToReceiver(NextPayloadFrame(streamId, Payload.Empty))
        deferred.await()
    }

    @Test
    fun testRequestReplyWithCancel() = test {
        withTimeoutOrNull(100.milliseconds) { requester.requestResponse(Payload.Empty) }
        delay(100)
        assertEquals(2, connection.sentFrames.size)
        assertTrue(connection.sentFrames[0] is RequestFrame)
        assertTrue(connection.sentFrames[1] is CancelFrame)
    }

    @Test
    fun testChannelRequestCancellation() = test {
        val job = Job()
        val request = flow<Payload> { Job().join() }.onCompletion { job.complete() }
        val response = requester.requestChannel(request).launchIn(CoroutineScope(connection.job))
        delay(100)
        response.cancelAndJoin()
        delay(100)
        assertTrue(job.isCompleted)
    }

    @Test
    fun testChannelRequestCancellationWithPayload() = test {
        val job = Job()
        val request = flow { repeat(100) { emit(Payload.Empty) } }.onCompletion { job.complete() }
        val response = requester.requestChannel(request).launchIn(CoroutineScope(connection.job))
        delay(1000)
        response.cancelAndJoin()
        delay(100)
        assertTrue(job.isCompleted)
        val sent = connection.sentFrames.size
        assertTrue(sent > 0)
        delay(100)
        assertEquals(sent, connection.sentFrames.size)
    }

    @Test
    fun testChannelRequestServerSideCancellation() = test {
        var ch: SendChannel<Payload>? = null
        val request = channelFlow<Payload> {
            ch = this
            offer(Payload(byteArrayOf(1), byteArrayOf(2)))
            awaitClose()
        }
        val response = requester.requestChannel(request).launchIn(CoroutineScope(connection.job))
        delay(100)
        val requestFrame = connection.sentFrames.first()
        assertTrue(requestFrame is RequestFrame)
        assertEquals(FrameType.RequestChannel, requestFrame.type)
        connection.sendToReceiver(CancelFrame(requestFrame.streamId), CompletePayloadFrame(requestFrame.streamId))
        response.join()
        delay(100)
        assertTrue(response.isCompleted)
        assertEquals(1, connection.sentFrames.size)
        assertTrue(ch!!.isClosedForSend)
    }

    @Test
    fun testCorrectFrameOrder() = test {
        val delay = Job()
        val request = flow {
            delay.join()
            emit(Payload("INIT"))
            repeat(1000) {
                emit(Payload(it.toString()))
            }
        }

        requester.requestChannel(request).requesting { RequestStrategy(Int.MAX_VALUE) }.launchIn(CoroutineScope(connection.job))
        delay(100)
        delay.complete()
        delay(100)
        assertEquals(1, connection.sentFrames.size)
        delay(100)
        assertEquals(1, connection.sentFrames.size)
        val requestFrame = connection.sentFrames.first()
        assertTrue(requestFrame is RequestFrame)
        assertEquals(FrameType.RequestChannel, requestFrame.type)
        assertEquals(Int.MAX_VALUE, requestFrame.initialRequest)
        assertEquals("INIT", requestFrame.payload.data.readText())
    }

    private fun streamIsTerminatedOnConnectionClose(request: suspend () -> Unit) = test {
        launch(connection.job) {
            delay(1.seconds)
            connection.cancel()
        }
        assertFailsWith(CancellationException::class) { request() }

        assertFailsWith(CancellationException::class) { request() }
    }

    @Test
    fun rrTerminatedOnConnectionClose() = streamIsTerminatedOnConnectionClose { requester.requestResponse(Payload.Empty) }

    @Test
    fun rsTerminatedOnConnectionClose() = streamIsTerminatedOnConnectionClose { requester.requestStream(Payload.Empty).collect() }

    @Test
    fun rcTerminatedOnConnectionClose() = streamIsTerminatedOnConnectionClose { requester.requestChannel(flowOf(Payload.Empty)).collect() }

}
