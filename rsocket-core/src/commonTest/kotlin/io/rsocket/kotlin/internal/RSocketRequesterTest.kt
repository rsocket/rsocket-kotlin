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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.time.*

class RSocketRequesterTest : TestWithConnection(), TestWithLeakCheck {
    private lateinit var requester: RSocketRequester

    override suspend fun before() {
        super.before()

        val state = RSocketState(connection, KeepAlive(1000.seconds, 1000.seconds))
        requester = RSocketRequester(state, StreamId.client())
        state.start(RSocketRequestHandler { })
    }

    @Test
    fun testInvalidFrameOnStream0() = test {
        connection.sendToReceiver(NextPayloadFrame(0, payload("data", "metadata"))) //should be just released
        delay(100)
        assertTrue(requester.isActive)
    }

    @Test
    fun testStreamInitialN() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).buffer(5)

            expectNoEventsIn(200)
            flow.launchIn(connection)

            expectFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(5, frame.initialRequest)
            }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamBuffer() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).buffer(2).take(2)

            expectNoEventsIn(200)
            flow.launchIn(connection)

            expectFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(2, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectFrame { frame ->
                assertTrue(frame is CancelFrame)
            }

            expectNoEventsIn(200)
        }
    }

    class SomeContext(val context: Int) : AbstractCoroutineContextElement(SomeContext) {
        companion object Key : CoroutineContext.Key<SomeContext>
    }

    @Test
    fun testStreamBufferWithAdditionalContext() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).buffer(2).flowOn(SomeContext(2)).take(2)

            expectNoEventsIn(200)
            flow.launchIn(connection)

            expectFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(2, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectFrame { frame ->
                assertTrue(frame is CancelFrame)
            }
            expectNoEventsIn(200)
        }
    }

    @Test //ignored on native because of dispatcher switching
    fun testStreamBufferWithAnotherDispatcher() = test(ignoreNative = true) {
        connection.test {
            val flow =
                requester.requestStream(Payload.Empty)
                    .buffer(2)
                    .flowOn(anotherDispatcher) //change dispatcher before take
                    .take(2)
                    .transform { emit(it) } //force using SafeCollector to check that `Flow invariant is violated` not happens

            expectNoEventsIn(200)
            flow.launchIn(connection)

            expectFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(2, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200) //will fail here if `Flow invariant is violated`
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectFrame { frame ->
                assertTrue(frame is CancelFrame)
            }

            expectNoEventsIn(200)
        }
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

        connection.test {
            expectFrame { frame ->
                val streamId = frame.streamId
                connection.sendToReceiver(ErrorFrame(streamId, RSocketError.ApplicationError(errorMessage)))
            }
            assertFailsWith(RSocketError.ApplicationError::class, errorMessage) { deferred.await() }
        }
    }

    @Test
    fun testHandleValidFrame() = test {
        connection.test {
            val deferred = async { requester.requestResponse(Payload.Empty) }
            expectFrame { frame ->
                val streamId = frame.streamId
                connection.sendToReceiver(NextPayloadFrame(streamId, Payload.Empty))
            }
            deferred.await()
            expectNoEventsIn(200)
        }
    }

    @Test
    fun testRequestReplyWithCancel() = test {
        connection.test {
            withTimeoutOrNull(100) { requester.requestResponse(Payload.Empty) }

            expectFrame { assertTrue(it is RequestFrame) }
            expectFrame { assertTrue(it is CancelFrame) }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testChannelRequestCancellation() = test {
        val job = Job()
        val request = flow<Payload> { Job().join() }.onCompletion { job.complete() }
        val response = requester.requestChannel(request).launchIn(connection)
        connection.test {
            expectNoEventsIn(200)
            response.cancelAndJoin()
            expectNoEventsIn(200)
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun testChannelRequestCancellationWithPayload() = test {
        val job = Job()
        val request = flow { repeat(100) { emit(Payload.Empty) } }.onCompletion { job.complete() }
        val response = requester.requestChannel(request).launchIn(connection)
        connection.test {
            expectFrame { assertTrue(it is RequestFrame) }
            expectNoEventsIn(200)
            response.cancelAndJoin()
            expectFrame { assertTrue(it is CancelFrame) }
            expectNoEventsIn(200)
            assertTrue(job.isCompleted)
        }
    }

    @Test //ignored on native because of coroutines bug with channels
    fun testChannelRequestServerSideCancellation() = test(ignoreNative = true) {
        var ch: SendChannel<Payload>? = null
        val request = channelFlow<Payload> {
            ch = this
            offer(payload(byteArrayOf(1), byteArrayOf(2)))
            awaitClose()
        }
        val response = requester.requestChannel(request).launchIn(connection)
        connection.test {
            expectFrame { frame ->
                val streamId = frame.streamId
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestChannel, frame.type)
                frame.release()
                connection.sendToReceiver(CancelFrame(streamId), CompletePayloadFrame(streamId))
            }
            response.join()
            expectNoEventsIn(200)
            assertTrue(response.isCompleted)
            assertTrue(ch!!.isClosedForSend)
        }
    }

    @Test
    fun testCorrectFrameOrder() = test {
        val delay = Job()
        val request = flow {
            delay.join()
            emit(payload("INIT"))
            repeat(1000) {
                emit(payload(it.toString()))
            }
        }

        requester.requestChannel(request).buffer(Int.MAX_VALUE).launchIn(connection)
        connection.test {
            expectNoEventsIn(200)
            delay.complete()
            expectFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestChannel, frame.type)
                assertEquals(Int.MAX_VALUE, frame.initialRequest)
                assertEquals("INIT", frame.payload.data.readText())
            }
            expectNoEventsIn(200)
        }
    }

    private fun streamIsTerminatedOnConnectionClose(request: suspend () -> Unit) = test {
        connection.launch {
            connection.test {
                expectFrame { assertTrue(it is RequestFrame) }
                connection.job.cancel()
                expectNoEventsIn(200)
            }
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
