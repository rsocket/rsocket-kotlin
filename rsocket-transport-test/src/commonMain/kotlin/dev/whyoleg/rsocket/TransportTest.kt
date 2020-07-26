package dev.whyoleg.rsocket

import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

abstract class TransportTest(private val timeout: Duration? = 3.minutes) {
    private var client: RSocket? = null

    abstract suspend fun init(): RSocket

    private suspend fun client(): RSocket = when (val c = client) {
        null -> init().also { client = it }
        else -> c
    }

    @AfterTest
    fun clean() = test {
        client().job.cancelAndJoin()
    }

    @Suppress("FunctionName")
    private fun Payload(metadataPresent: Int): Payload {
        val metadata = when (metadataPresent % 5) {
            0 -> null
            1 -> ""
            else -> MOCK_METADATA
        }
        return Payload(metadata, MOCK_DATA)
    }

    @Test
    fun fireAndForget10() = test(timeout) {
        val client = client()
        (1..10).map { async { client.fireAndForget(Payload(it)) } }.awaitAll()
    }

    @Test
    fun largePayloadFireAndForget10() = test(timeout) {
        val client = client()
        (1..10).map { async { client.fireAndForget(LARGE_PAYLOAD) } }.awaitAll()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun metadataPush10() = test(timeout) {
        val client = client()
        (1..10).map { async { client.metadataPush(MOCK_DATA.encodeToByteArray()) } }.awaitAll()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun largePayloadMetadataPush10() = test(timeout) {
        val client = client()
        (1..10).map { async { client.metadataPush(LARGE_DATA.encodeToByteArray()) } }.awaitAll()
    }

    @Test
    fun requestChannel0() = test(10.seconds) {
        val client = client()
        val list = client.requestChannel(emptyFlow()).toList()
        assertTrue(list.isEmpty())
    }

    @Test
    fun requestChannel1() = test(10.seconds) {
        val client = client()
        val list = client.requestChannel(flowOf(Payload(0))).toList()
        assertEquals(1, list.size)
    }

    @Test
    fun requestChannel3() = test(timeout) {
        val client = client()
        val request = RequestingFlow {
            repeat(3) { emit(Payload(it)) }
        }
        val list = client.requestChannel(request).requesting(RequestStrategy(3)).toList()
        assertEquals(3, list.size)
    }

    @Test
    fun largePayloadRequestChannel200() = test(timeout) {
        val client = client()
        val request = RequestingFlow {
            repeat(200) { emit(LARGE_PAYLOAD) }
        }
        val list = client.requestChannel(request).requesting(RequestStrategy(Int.MAX_VALUE)).toList()
        assertEquals(200, list.size)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun requestChannel20000() = test(timeout) {
        val client = client()
        val request = RequestingFlow {
            repeat(20_000) { emit(Payload(7)) }
        }
        val list = client.requestChannel(request).requesting(RequestStrategy(Int.MAX_VALUE)).onEach {
            assertEquals(MOCK_DATA, it.data.decodeToString())
            assertEquals(MOCK_METADATA, it.metadata?.decodeToString())
        }.toList()
        assertEquals(20_000, list.size)
    }

    @Test
    fun requestChannel200000() = test(timeout) {
        val client = client()
        val request = RequestingFlow {
            repeat(200_000) { emit(Payload(it)) }
        }
        val list = client.requestChannel(request).requesting(RequestStrategy(Int.MAX_VALUE)).toList()
        assertEquals(200_000, list.size)
    }

    //    @Test
    fun requestChannel2000000() = test(3.minutes) {
        val client = client()
        val request = RequestingFlow {
            repeat(2_000_000) { emit(Payload(it)) }
        }
        val list = client.requestChannel(request).requesting(RequestStrategy(Int.MAX_VALUE)).toList()
        assertEquals(2_000_000, list.size)
    }

    @Test
    fun requestChannel512x1024() = test(null) {
        val client = client()
        val request = RequestingFlow {
            repeat(512) {
                emit(Payload(it))
            }
        }
        (0..1024).map {
            async {
                withTimeout(3.minutes) {
                    val list = client.requestChannel(request).toList()
                    assertEquals(512, list.size)
                }
            }
        }.awaitAll()
    }

    @Test
    fun requestResponse1() = test(timeout) {
        val client = client()
        client.requestResponse(Payload(1)).let(::checkPayload)
    }

    @Test
    fun requestResponse10() = test(timeout) {
        val client = client()
        (1..10).map { async { client.requestResponse(Payload(it)).let(::checkPayload) } }.awaitAll()
    }

    @Test
    fun requestResponse100() = test(timeout) {
        val client = client()
        (1..100).map { async { client.requestResponse(Payload(it)).let(::checkPayload) } }.awaitAll()
    }

    @Test
    fun largePayloadRequestResponse100() = test(timeout) {
        val client = client()
        (1..100).map { async { client.requestResponse(LARGE_PAYLOAD) } }.awaitAll()
    }

    @Test
    fun requestResponse10000() = test(timeout) {
        val client = client()
        (1..10000).map { async { client.requestResponse(Payload(3)).let(::checkPayload) } }.awaitAll()
    }

    @Test
    fun requestStream5() = test(timeout) {
        val client = client()
        val list = client.requestStream(Payload(3)).onEach { checkPayload(it) }.take(5).toList()
        assertEquals(5, list.size)
    }

    @Test
    fun requestStream10000() = test(timeout) {
        val client = client()
        val list = client.requestStream(Payload(3)).onEach { checkPayload(it) }.toList()
        assertEquals(10000, list.size)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun checkPayload(payload: Payload) {
        assertEquals(TestRSocket.data, payload.data.decodeToString())
        assertEquals(TestRSocket.metadata, payload.metadata?.decodeToString())
    }

    companion object {
        val MOCK_DATA: String = "test-data"
        val MOCK_METADATA: String = "metadata"
        val LARGE_DATA by lazy { readLargePayload("words.shakespeare.txt.gz") }
        val LARGE_PAYLOAD by lazy { Payload(LARGE_DATA, LARGE_DATA) }
    }
}

class TestRSocket : RSocket {
    override val job: Job = Job()

    override fun metadataPush(metadata: ByteArray): Unit = Unit

    override fun fireAndForget(payload: Payload): Unit = Unit

    override suspend fun requestResponse(payload: Payload): Payload = Payload(metadata, data)

    override fun requestStream(payload: Payload): RequestingFlow<Payload> = RequestingFlow {
        repeat(10000) { emit(requestResponse(payload)) }
    }

    override fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> {
        return RequestingFlow {
            payloads.collect { emit(it) }
        }
    }

    companion object {
        const val data = "hello world"
        const val metadata = "metadata"
    }
}
