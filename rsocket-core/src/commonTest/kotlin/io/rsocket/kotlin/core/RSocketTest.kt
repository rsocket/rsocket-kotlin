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

package io.rsocket.kotlin.core

import app.cash.turbine.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RSocketTest : SuspendTest, TestWithLeakCheck {

    private val testJob: Job = Job()

    override suspend fun after() {
        super.after()
        testJob.cancelAndJoin()
    }

    private suspend fun start(acceptor: ConnectionAcceptor): ConnectedRSocket {
        val localServer = TestServer().bindIn(
            CoroutineScope(Dispatchers.Unconfined + testJob + TestExceptionHandler),
            LocalServerTransport(InUseTrackingPool),
            acceptor
        )

        return TestConnector {
            connectionConfig {
                keepAlive = KeepAlive(1000.seconds, 1000.seconds)
            }
        }.connect(localServer)
    }

    private suspend fun start(handler: RSocket? = null): ConnectedRSocket = start {
        handler ?: RSocket {
            onRequestResponse { it }
            onRequestStream {
                it.close()
                flow { repeat(10) { emitOrClose(payload("server got -> [$it]")) } }
            }
            onRequestChannel { init, payloads ->
                init.close()
                payloads.onEach { it.close() }.launchIn(requester.session)
                flow { repeat(10) { emitOrClose(payload("server got -> [$it]")) } }
            }
        }
    }

    @Test
    fun testRequestResponseNoError() = test {
        val requester = start()
        requester.requestResponse(payload("HELLO")).close()
    }

    @Test
    fun testRequestResponseError() = test {
        val requester = start(RSocket {
            onRequestResponse { error("stub") }
        })
        assertFailsWith(RSocketError.ApplicationError::class) { requester.requestResponse(payload("HELLO")) }
    }

    @Test
    fun testRequestResponseCustomError() = test {
        val requester = start(RSocket {
            onRequestResponse { throw RSocketError.Custom(0x00000501, "stub") }
        })
        val error = assertFailsWith(RSocketError.Custom::class) { requester.requestResponse(payload("HELLO")) }
        assertEquals(0x00000501, error.errorCode)
    }

    @Test
    fun testStream() = test {
        val requester = start()
        requester.requestStream(payload("HELLO")).test {
            repeat(10) {
                awaitItem().close()
            }
            awaitComplete()
        }
    }

    @Test
    fun testStreamResponderError() = test {
        var p: Payload? = null
        val requester = start(RSocket {
            onRequestStream {
                //copy payload, for some specific usage, and don't release original payload
                val text = it.copy().use { it.data.readText() }
                p = it
                //don't use payload
                flow {
                    emit(payload(text + "123"))
                    emit(payload(text + "456"))
                    emit(payload(text + "789"))
                    delay(200)
                    error("FAIL")
                }
            }
        })
        requester.requestStream(payload("HELLO")).requestBy(1, 0).test {
            repeat(3) {
                awaitItem().close()
            }
            val error = awaitError()
            assertTrue(error is RSocketError.ApplicationError)
            assertEquals("FAIL", error.message)
        }
        delay(100) //async cancellation
        assertEquals(0, p?.data?.remaining)
    }

    @Test
    fun testStreamRequesterError() = test {
        val requester = start(RSocket {
            onRequestStream {
                (0..100).asFlow().map {
                    payload(it.toString())
                }
            }
        })
        requester.requestStream(payload("HELLO"))
            .requestBy(10, 0)
            .withIndex()
            .onEach {
                if (it.index == 23) {
                    it.value.close()
                    error("oops")
                }
            }
            .map { it.value }
            .test {
                repeat(23) {
                    awaitItem().close()
                }
                val error = awaitError()
                assertTrue(error is IllegalStateException)
                assertEquals("oops", error.message)
            }
    }

    @Test
    fun testStreamCancel() = test {
        val requester = start(RSocket {
            onRequestStream {
                (0..100).asFlow().map {
                    payload(it.toString())
                }
            }
        })
        requester.requestStream(payload("HELLO"))
            .requestBy(15, 0)
            .take(3) //canceled after 3 element
            .test {
                repeat(3) {
                    awaitItem().close()
                }
                awaitComplete()
            }
    }

    @Test
    fun testStreamCancelWithChannel() = test {
        val requester = start(RSocket {
            onRequestStream {
                (0..100).asFlow().map {
                    payload(it.toString())
                }
            }
        })
        val channel = requester.requestStream(payload("HELLO"))
            .requestBy(5, 0)
            .take(18) //canceled after 18 element
            .produceIn(this)

        repeat(18) {
            channel.receive().close()
        }
        assertTrue(channel.receiveCatching().isClosed)
    }

    @Test
    fun testChannel() = test {
        val awaiter = Job()
        val requester = start()
        val request = (1..10).asFlow().map { payload(it.toString()) }.onCompletion { awaiter.complete() }
        requester.requestChannel(payload(""), request).test {
            repeat(10) {
                awaitItem().close()
            }
            awaitComplete()
        }
        awaiter.join()
        delay(500)
    }

    @Test
    fun testErrorPropagatesCorrectly() = test {
        val error = CompletableDeferred<Throwable>()
        val requester = start(RSocket {
            onRequestChannel { init, payloads ->
                init.close()
                payloads.catch { error.complete(it) }
            }
        })
        val request = flow<Payload> { error("test") }
        //TODO
        kotlin.runCatching {
            requester.requestChannel(Payload.Empty, request).collect()
        }.also(::println)
        val e = error.await()
        assertTrue(e is RSocketError.ApplicationError)
        assertEquals("test", e.message)
    }

    @Test
    fun testRequestPropagatesCorrectlyForRequestChannel() = test {
        val requester = start(RSocket {
            onRequestChannel { init, payloads ->
                init.close()
                payloads.requestBy(3, 0).take(3)
            }
        })
        val request = (1..3).asFlow().map { payload(it.toString()) }
        requester.requestChannel(payload("0"), request).requestBy(3, 0).test {
            repeat(3) {
                awaitItem().close()
            }
            awaitComplete()
        }
    }

    private val requesterPayloads
        get() = listOf(
            payload("d1", "m1"),
            payload("d2"),
            payload("d3", "m3"),
            payload("d4"),
            payload("d5", "m5")
        )

    private val responderPayloads
        get() = listOf(
            payload("rd1", "rm1"),
            payload("rd2"),
            payload("rd3", "rm3"),
            payload("rd4"),
            payload("rd5", "rm5")
        )

    @Test
    fun requestChannelIsTerminatedAfterBothSidesSentCompletion1() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(
            requesterSendChannel,
            responderSendChannel
        )

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        complete(requesterSendChannel, responderReceiveChannel)

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)
    }

    @Test
    fun requestChannelTerminatedAfterBothSidesSentCompletion2() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(
            requesterSendChannel,
            responderSendChannel
        )

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        complete(requesterSendChannel, responderReceiveChannel)
    }

    @Test
    fun requestChannelCancellationFromResponderShouldLeaveStreamInHalfClosedStateWithNextCompletionPossibleFromRequester() =
        test {
            val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
            val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
            val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(
                requesterSendChannel,
                responderSendChannel
            )

            sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
            cancel(requesterSendChannel, responderReceiveChannel)

            sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
            complete(responderSendChannel, requesterReceiveChannel)
        }

    @Test
    fun requestChannelCompletionFromRequesterShouldLeaveStreamInHalfClosedStateWithNextCancellationPossibleFromResponder() =
        test {
            val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
            val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
            val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(
                requesterSendChannel,
                responderSendChannel
            )

            sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
            complete(responderSendChannel, requesterReceiveChannel)

            sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
            cancel(requesterSendChannel, responderReceiveChannel)
        }

    @Test
    fun requestChannelEnsureThatRequesterSubscriberCancellationTerminatesStreamsOnBothSides() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(
            requesterSendChannel,
            responderSendChannel
        )

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)

        requesterReceiveChannel.cancel()
        delay(1000)

        assertTrue(requesterSendChannel.isClosedForSend)
        assertTrue(responderSendChannel.isClosedForSend)
        assertTrue(requesterReceiveChannel.isClosedForReceive)
        assertTrue(responderReceiveChannel.isClosedForReceive)
    }

    private suspend fun initRequestChannel(
        requesterSendChannel: Channel<Payload>,
        responderSendChannel: Channel<Payload>,
    ): Pair<ReceiveChannel<Payload>, ReceiveChannel<Payload>> {
        val responderDeferred = CompletableDeferred<ReceiveChannel<Payload>>()
        val requester = start {
            RSocket {
                onRequestChannel { init, payloads ->
                    responderDeferred.complete(payloads.onStart { emit(init) }.produceIn(requester.session))

                    responderSendChannel.consumeAsFlow()
                }
            }
        }
        val requesterReceiveChannel =
            requester
                .requestChannel(payload("initData", "initMetadata"), requesterSendChannel.consumeAsFlow())
                .produceIn(requester.session)

        val responderReceiveChannel = responderDeferred.await()

        responderReceiveChannel.checkReceived(payload("initData", "initMetadata"))
        return requesterReceiveChannel to responderReceiveChannel
    }

    private suspend inline fun complete(sendChannel: SendChannel<Payload>, receiveChannel: ReceiveChannel<Payload>) {
        sendChannel.close()
        delay(100)
        assertTrue(receiveChannel.isClosedForReceive, "receiveChannel.isClosedForReceive=true")
    }

    private suspend inline fun cancel(
        requesterChannel: SendChannel<Payload>,
        responderChannel: ReceiveChannel<Payload>,
    ) {
        responderChannel.cancel()
        delay(100)
        assertTrue(requesterChannel.isClosedForSend, "requesterChannel.isClosedForSend=true")
    }

    private suspend fun sendAndCheckReceived(
        requesterChannel: SendChannel<Payload>,
        responderChannel: ReceiveChannel<Payload>,
        payloads: List<Payload>,
    ) {
        delay(100)
        assertFalse(requesterChannel.isClosedForSend, "requesterChannel.isClosedForSend=false")
        assertFalse(responderChannel.isClosedForReceive, "responderChannel.isClosedForReceive=false")
        payloads.forEach { requesterChannel.send(it.copy()) } //TODO?
        payloads.forEach { responderChannel.checkReceived(it) }
    }

    private suspend fun ReceiveChannel<Payload>.checkReceived(otherPayload: Payload) {
        val payload = receive()
        assertEquals(payload.metadata?.readText(), otherPayload.metadata?.readText())
        assertEquals(payload.data.readText(), otherPayload.data.readText())
    }

}
