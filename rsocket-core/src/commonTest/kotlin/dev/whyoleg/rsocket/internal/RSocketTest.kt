package dev.whyoleg.rsocket.internal

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.connection.*
import dev.whyoleg.rsocket.error.*
import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.keepalive.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

@FlowPreview
@OptIn(ExperimentalCoroutinesApi::class)
class RSocketTest {
    lateinit var requester: RSocket

    private fun start(handler: RSocket? = null) {
        val clientChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverConnection = LocalConnection("server", clientChannel, serverChannel)
        val clientConnection = LocalConnection("client", serverChannel, clientChannel)
        val requestHandler = handler ?: RSocketRequestHandler {
            requestResponse = { it }
            requestStream = {
                RequestingFlow {
                    repeat(10) { emit(Payload(null, "server got -> [$it]")) }
                }
            }
            requestChannel = {
                it.launchIn(CoroutineScope(job))
                RequestingFlow {
                    repeat(10) { emit(Payload(null, "server got -> [$it]")) }
                }
            }
        }

        fun state(connection: Connection): RSocketState =
            RSocketStateImpl(connection, KeepAlive(1000.seconds, 1000.seconds), RequestStrategy.Default, {})

        val clientState = state(clientConnection)
        requester = RSocketRequester(clientState, StreamId.client())
        clientState.start(RSocketRequestHandler { })
        state(serverConnection).start(requestHandler)
    }

    @Test
    fun testRequestResponseNoError() = test {
        start()
        requester.requestResponse(Payload(null, "HELLO"))
    }

    @Test
    fun testRequestResponseError() = test {
        start(RSocketRequestHandler {
            requestResponse = { error("stub") }
        })
        assertFailsWith(RSocketError.ApplicationError::class) { requester.requestResponse(Payload(null, "HELLO")) }
    }

    @Test
    fun testRequestResponseCustomError() = test {
        start(RSocketRequestHandler {
            requestResponse = { throw RSocketError.Custom(0x00000501, "stub") }
        })
        val error = assertFailsWith(RSocketError.Custom::class) { requester.requestResponse(Payload(null, "HELLO")) }
        assertEquals(0x00000501, error.errorCode)
    }

    @Test
    fun testStream() = test {
        start()
        val response = requester.requestStream(Payload.Empty).toList()
        assertEquals(10, response.size)
    }

    @Test
    fun testChannel() = test {
        start()
        val request = (1..10).asFlow().map { Payload(null, it.toString()) }
        val response = requester.requestChannel(request).toList()
        assertEquals(10, response.size)
    }

    @Test
    fun testErrorPropagatesCorrectly() = test {
        val error = CompletableDeferred<Throwable>()
        start(RSocketRequestHandler {
            requestChannel = { it.intercept { catch { error.complete(it) } } }
        })
        val request = flow<Payload> { error("test") }
        val response = requester.requestChannel(request)
        assertFails { response.collect() }.also(::println)
        delay(100)
        assertTrue(error.isActive)
    }

    @Test
    fun testRequestPropagatesCorrectlyForRequestChannel() = test {
        start(RSocketRequestHandler {
            requestChannel = { it.requesting(RequestStrategy(3)).take(3).onRequest() }
        })
        val request = (1..3).asFlow().map { Payload(null, it.toString()) }
        val response = requester.requestChannel(request).requesting(RequestStrategy(3)).toList()
        assertEquals(3, response.size)
    }

    private val requesterPayloads = listOf(
        Payload("m1", "d1"),
        Payload(null, "d2"),
        Payload("m3", "d3"),
        Payload(null, "d4"),
        Payload("m5", "d5")
    )

    private val responderPayloads = listOf(
        Payload("rm1", "rd1"),
        Payload(null, "rd2"),
        Payload("rm3", "rd3"),
        Payload(null, "rd4"),
        Payload("rm5", "rd5")
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
        println(requesterSendChannel)
        println(responderSendChannel)
        println(requesterReceiveChannel)
        println(responderReceiveChannel)

        assertTrue(requesterSendChannel.isClosedForSend)
        assertTrue(responderSendChannel.isClosedForSend)
        assertTrue(requesterReceiveChannel.isClosedForReceive)
        assertTrue(responderReceiveChannel.isClosedForReceive)
    }

    private suspend fun initRequestChannel(
        requesterSendChannel: Channel<Payload>,
        responderSendChannel: Channel<Payload>
    ): Pair<ReceiveChannel<Payload>, ReceiveChannel<Payload>> {
        val responderDeferred = CompletableDeferred<ReceiveChannel<Payload>>()
        start(RSocketRequestHandler {
            requestChannel = {
                responderDeferred.complete(it.produceIn(CoroutineScope(job)))
                responderSendChannel.consumeAsFlow().onRequest()
            }
        })
        val requesterReceiveChannel =
            requester.requestChannel(requesterSendChannel.consumeAsFlow()).produceIn(CoroutineScope(requester.job))

        requesterSendChannel.send(Payload("initMetadata", "initData"))

        val responderReceiveChannel = responderDeferred.await()

        responderReceiveChannel.checkReceived(Payload("initMetadata", "initData"))
        return requesterReceiveChannel to responderReceiveChannel
    }

    private suspend inline fun complete(sendChannel: SendChannel<Payload>, receiveChannel: ReceiveChannel<Payload>) {
        sendChannel.close()
        delay(100)
        println(receiveChannel)
        assertTrue(receiveChannel.isClosedForReceive)
    }

    private suspend inline fun cancel(requesterChannel: SendChannel<Payload>, responderChannel: ReceiveChannel<Payload>) {
        responderChannel.cancel()
        delay(100)
        println(requesterChannel)
        assertTrue(requesterChannel.isClosedForSend)
    }

    private suspend fun sendAndCheckReceived(
        requesterChannel: SendChannel<Payload>,
        responderChannel: ReceiveChannel<Payload>,
        payloads: List<Payload>
    ) {
        delay(100)
        assertFalse(requesterChannel.isClosedForSend)
        assertFalse(responderChannel.isClosedForReceive)
        payloads.forEach { requesterChannel.send(it) }
        payloads.forEach { responderChannel.checkReceived(it) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun ReceiveChannel<Payload>.checkReceived(otherPayload: Payload) {
        val payload = receive()
        assertEquals(payload.metadata?.decodeToString(), otherPayload.metadata?.decodeToString())
        assertEquals(payload.data.decodeToString(), otherPayload.data.decodeToString())
    }

}
