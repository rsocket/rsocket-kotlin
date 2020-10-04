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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

class RSocketTest : SuspendTest {

    lateinit var serverConnection: LocalConnection
    lateinit var clientConnection: LocalConnection

    override suspend fun before() {
        super.before()

        val clientChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)

        serverConnection = LocalConnection("server", clientChannel, serverChannel)
        clientConnection = LocalConnection("client", serverChannel, clientChannel)

    }

    override suspend fun after() {
        super.after()

        serverConnection.job.cancelAndJoin()
        clientConnection.job.cancelAndJoin()
    }

    private suspend fun start(handler: RSocket? = null): RSocket = coroutineScope {
        launch {
            serverConnection.startServer {
                handler ?: RSocketRequestHandler {
                    requestResponse = { it }
                    requestStream = {
                        flow { repeat(10) { emit(Payload("server got -> [$it]")) } }
                    }
                    requestChannel = {
                        it.launchIn(CoroutineScope(job))
                        flow { repeat(10) { emit(Payload("server got -> [$it]")) } }
                    }
                }
            }
        }
        clientConnection.connectClient(
            RSocketConnectorConfiguration(
                keepAlive = KeepAlive(1000.seconds, 1000.seconds),
                loggerFactory = TestLoggerFactory
            )
        )
    }

    @Test
    fun testRequestResponseNoError() = test {
        val requester = start()
        requester.requestResponse(Payload("HELLO"))
    }

    @Test
    fun testRequestResponseError() = test {
        val requester = start(RSocketRequestHandler {
            requestResponse = { error("stub") }
        })
        assertFailsWith(RSocketError.ApplicationError::class) { requester.requestResponse(Payload("HELLO")) }
    }

    @Test
    fun testRequestResponseCustomError() = test {
        val requester = start(RSocketRequestHandler {
            requestResponse = { throw RSocketError.Custom(0x00000501, "stub") }
        })
        val error = assertFailsWith(RSocketError.Custom::class) { requester.requestResponse(Payload("HELLO")) }
        assertEquals(0x00000501, error.errorCode)
    }

    @Test
    fun testStream() = test {
        val requester = start()
        val response = requester.requestStream(Payload.Empty).toList()
        assertEquals(10, response.size)
    }

    @Test
    fun testChannel() = test {
        val requester = start()
        val request = (1..10).asFlow().map { Payload(it.toString()) }
        val response = requester.requestChannel(request).toList()
        assertEquals(10, response.size)
    }

    @Test
    fun testErrorPropagatesCorrectly() = test {
        val error = CompletableDeferred<Throwable>()
        val requester = start(RSocketRequestHandler {
            requestChannel = { it.catch { error.complete(it) } }
        })
        val request = flow<Payload> { error("test") }
        val response = requester.requestChannel(request)
        assertFails { response.collect() }
        delay(100)
        assertTrue(error.isActive)
    }

    @Test
    fun testRequestPropagatesCorrectlyForRequestChannel() = test {
        val requester = start(RSocketRequestHandler {
            requestChannel = { it.buffer(3).take(3) }
        })
        val request = (1..3).asFlow().map { Payload(it.toString()) }
        val response = requester.requestChannel(request).buffer(3).toList()
        assertEquals(3, response.size)
    }

    private val requesterPayloads = listOf(
        Payload("d1", "m1"),
        Payload("d2"),
        Payload("d3", "m3"),
        Payload("d4"),
        Payload("d5", "m5")
    )

    private val responderPayloads = listOf(
        Payload("rd1", "rm1"),
        Payload("rd2"),
        Payload("rd3", "rm3"),
        Payload("rd4"),
        Payload("rd5", "rm5")
    )

    @Test
    fun requestChannelCase_StreamIsTerminatedAfterBothSidesSentCompletion1() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(requesterSendChannel, responderSendChannel)

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        complete(requesterSendChannel, responderReceiveChannel)

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)
    }

    @Test
    fun requestChannelCase_StreamIsTerminatedAfterBothSidesSentCompletion2() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(requesterSendChannel, responderSendChannel)

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        complete(requesterSendChannel, responderReceiveChannel)
    }

    @Test
    fun requestChannelCase_CancellationFromResponderShouldLeaveStreamInHalfClosedStateWithNextCompletionPossibleFromRequester() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(requesterSendChannel, responderSendChannel)

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        cancel(requesterSendChannel, responderReceiveChannel)

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)
    }

    @Test
    fun requestChannelCase_CompletionFromRequesterShouldLeaveStreamInHalfClosedStateWithNextCancellationPossibleFromResponder() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(requesterSendChannel, responderSendChannel)

        sendAndCheckReceived(responderSendChannel, requesterReceiveChannel, responderPayloads)
        complete(responderSendChannel, requesterReceiveChannel)

        sendAndCheckReceived(requesterSendChannel, responderReceiveChannel, requesterPayloads)
        cancel(requesterSendChannel, responderReceiveChannel)
    }

    @Test
    fun requestChannelCase_ensureThatRequesterSubscriberCancellationTerminatesStreamsOnBothSides() = test {
        val requesterSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val responderSendChannel = Channel<Payload>(Channel.UNLIMITED)
        val (requesterReceiveChannel, responderReceiveChannel) = initRequestChannel(requesterSendChannel, responderSendChannel)

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
        val requester = start(RSocketRequestHandler {
            requestChannel = {
                responderDeferred.complete(it.produceIn(CoroutineScope(job)))
                responderSendChannel.consumeAsFlow()
            }
        })
        val requesterReceiveChannel =
            requester.requestChannel(requesterSendChannel.consumeAsFlow()).produceIn(CoroutineScope(requester.job))

        requesterSendChannel.send(Payload("initData", "initMetadata"))

        val responderReceiveChannel = responderDeferred.await()

        responderReceiveChannel.checkReceived(Payload("initData", "initMetadata"))
        return requesterReceiveChannel to responderReceiveChannel
    }

    private suspend inline fun complete(sendChannel: SendChannel<Payload>, receiveChannel: ReceiveChannel<Payload>) {
        sendChannel.close()
        delay(100)
        assertTrue(receiveChannel.isClosedForReceive)
    }

    private suspend inline fun cancel(requesterChannel: SendChannel<Payload>, responderChannel: ReceiveChannel<Payload>) {
        responderChannel.cancel()
        delay(100)
        assertTrue(requesterChannel.isClosedForSend)
    }

    private suspend fun sendAndCheckReceived(
        requesterChannel: SendChannel<Payload>,
        responderChannel: ReceiveChannel<Payload>,
        payloads: List<Payload>,
    ) {
        delay(100)
        assertFalse(requesterChannel.isClosedForSend)
        assertFalse(responderChannel.isClosedForReceive)
        payloads.forEach { requesterChannel.send(it.copy()) } //TODO?
        payloads.forEach { responderChannel.checkReceived(it) }
    }

    private suspend fun ReceiveChannel<Payload>.checkReceived(otherPayload: Payload) {
        val payload = receive()
        assertEquals(payload.metadata?.readText(), otherPayload.metadata?.readText())
        assertEquals(payload.data.readText(), otherPayload.data.readText())
    }

}
