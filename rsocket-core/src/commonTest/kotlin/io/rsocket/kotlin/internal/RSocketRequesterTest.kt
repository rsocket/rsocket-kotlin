/*
 * Copyright 2015-2024 the original author or authors.
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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RSocketRequesterTest : TestWithConnection() {
    private lateinit var requester: RSocket

    override suspend fun before() {
        super.before()

        requester = TestConnector {
            connectionConfig {
                keepAlive = KeepAlive(1000.seconds, 1000.seconds)
            }
        }.connect(connection)

        connection.ignoreSetupFrame()
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
            val flow = requester.requestStream(Payload.Empty).flowOn(PrefetchStrategy(5, 0))

            expectNoEventsIn(200)
            flow.launchIn(connection)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(5, frame.initialRequest)
            }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamRequestOnly() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).flowOn(PrefetchStrategy(2, 0)).take(2)

            expectNoEventsIn(200)
            flow.launchIn(connection)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(2, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is CancelFrame)
            }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamRequestWithContextSwitch() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).take(2).flowOn(PrefetchStrategy(1, 0))

            expectNoEventsIn(200)
            flow.launchIn(connection + anotherDispatcher)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(1, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is RequestNFrame)
                assertEquals(1, frame.requestN)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is CancelFrame)
            }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamRequestByFixed() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).flowOn(PrefetchStrategy(3, 0))

            expectNoEventsIn(200)
            flow.launchIn(connection)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(3, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is RequestNFrame)
                assertEquals(3, frame.requestN)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextCompletePayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamRequestBy() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).flowOn(PrefetchStrategy(5, 2))

            expectNoEventsIn(200)
            flow.launchIn(connection)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(5, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is RequestNFrame)
                assertEquals(5, frame.requestN)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
            connection.sendToReceiver(NextCompletePayloadFrame(1, Payload.Empty))

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testStreamRequestCancel() = test {
        connection.test {
            val flow = requester.requestStream(Payload.Empty).flowOn(PrefetchStrategy(1, 0)).take(3)

            expectNoEventsIn(200)
            flow.launchIn(connection)

            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestStream, frame.type)
                assertEquals(1, frame.initialRequest)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is RequestNFrame)
                assertEquals(1, frame.requestN)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
                assertTrue(frame is RequestNFrame)
                assertEquals(1, frame.requestN)
            }

            expectNoEventsIn(200)
            connection.sendToReceiver(NextPayloadFrame(1, Payload.Empty))

            awaitFrame { frame ->
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
        @OptIn(InternalCoroutinesApi::class)
        val error = requester.coroutineContext.job.getCancellationException().cause
        assertTrue(error is RSocketError.Setup.Rejected)
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun testHandleApplicationException() = test {
        val errorMessage = "error"
        val deferred = GlobalScope.async { requester.requestResponse(Payload.Empty) }

        connection.test {
            awaitFrame { frame ->
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
            awaitFrame { frame ->
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
            assertTrue(withTimeoutOrNull(100) { requester.requestResponse(Payload.Empty) } == null)

            awaitFrame { assertTrue(it is RequestFrame) }
            awaitFrame { assertTrue(it is CancelFrame) }

            expectNoEventsIn(200)
        }
    }

    @Test
    fun testChannelRequestCancellation() = test {
        val job = Job()
        val request = flow<Payload> { Job().join() }.onCompletion { job.complete() }
        val response = requester.requestChannel(Payload.Empty, request).launchIn(connection)
        connection.test {
            awaitFrame { assertTrue(it is RequestFrame) }
            expectNoEventsIn(200)
            response.cancelAndJoin()
            awaitFrame { assertTrue(it is CancelFrame) }
            expectNoEventsIn(200)
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun testChannelRequestCancellationWithPayload() = test {
        val job = Job()
        val request = flow { repeat(100) { emit(Payload.Empty) } }.onCompletion { job.complete() }
        val response = requester.requestChannel(Payload.Empty, request).launchIn(connection)
        connection.test {
            awaitFrame { assertTrue(it is RequestFrame) }
            expectNoEventsIn(200)
            response.cancelAndJoin()
            awaitFrame { assertTrue(it is CancelFrame) }
            expectNoEventsIn(200)
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun testChannelRequestServerSideCancellation() = test {
        var ch: SendChannel<Payload>? = null
        val request = channelFlow<Payload> {
            ch = this
            awaitClose()
        }
        val response = requester.requestChannel(payload(byteArrayOf(1), byteArrayOf(2)), request).launchIn(connection)
        connection.test {
            awaitFrame { frame ->
                val streamId = frame.streamId
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestChannel, frame.type)
                frame.close()
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
            repeat(1000) {
                emit(payload(it.toString()))
            }
        }

        requester.requestChannel(payload("INIT"), request).flowOn(PrefetchStrategy(Int.MAX_VALUE, 0))
            .launchIn(connection)
        connection.test {
            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
                assertEquals(FrameType.RequestChannel, frame.type)
                assertEquals(Int.MAX_VALUE, frame.initialRequest)
                assertEquals("INIT", frame.payload.data.readString())
            }
            expectNoEventsIn(200)
            delay.complete()
            expectNoEventsIn(200)
        }
    }

    private fun streamIsTerminatedOnConnectionClose(request: suspend () -> Unit) = test {
        connection.launch {
            delay(200)
            connection.test {
                awaitFrame { assertTrue(it is RequestFrame) }
                connection.cancel()
                awaitComplete()
            }
        }
        assertFailsWith(CancellationException::class) { request() }

        assertFailsWith(CancellationException::class) { request() }

        delay(200)
    }

    @Test
    fun rrTerminatedOnConnectionClose() =
        streamIsTerminatedOnConnectionClose { requester.requestResponse(Payload.Empty) }

    @Test
    fun rsTerminatedOnConnectionClose() =
        streamIsTerminatedOnConnectionClose { requester.requestStream(Payload.Empty).collect() }

    @Test
    fun rcTerminatedOnConnectionClose() =
        streamIsTerminatedOnConnectionClose { requester.requestChannel(Payload.Empty, emptyFlow()).collect() }

    @Test
    fun cancelRequesterToCloseConnection() = test {
        val request = requester.requestStream(Payload.Empty).produceIn(GlobalScope)
        connection.test {
            awaitFrame { frame ->
                assertTrue(frame is RequestFrame)
            }
            requester.cancel() //cancel requester
            awaitFrame { assertTrue(it is ErrorFrame) }
            awaitError()
        }
        delay(100)
        assertFalse(connection.isActive)
        assertTrue(request.isClosedForReceive)
    }

}
