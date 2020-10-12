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

package io.rsocket.kotlin.test

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

@OptIn(ExperimentalTime::class)
abstract class TransportTest : SuspendTest {
    override val testTimeout: Duration = TransportTestDefaultDuration

    lateinit var client: RSocket //should be assigned in `before`

    override suspend fun after() {
        client.job.cancelAndJoin()
    }

    @Test
    fun fireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(payload(it)) } }.awaitAll()
    }

    @Test
    fun largePayloadFireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(LARGE_PAYLOAD) } }.awaitAll()
    }

    @Test
    fun metadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(MOCK_DATA)) } }.awaitAll()
    }

    @Test
    fun largePayloadMetadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(LARGE_DATA)) } }.awaitAll()
    }

    @Test
    fun requestChannel0() = test(10.seconds) {
        val list = client.requestChannel(emptyFlow()).toList()
        assertTrue(list.isEmpty())
    }

    @Test
    fun requestChannel1() = test(10.seconds) {
        val list = client.requestChannel(flowOf(payload(0))).onEach { it.release() }.toList()
        assertEquals(1, list.size)
    }

    @Test
    fun requestChannel3() = test {
        val request = flow {
            repeat(3) { emit(payload(it)) }
        }
        val list = client.requestChannel(request).buffer(3).onEach { it.release() }.toList()
        assertEquals(3, list.size)
    }

    @Test
    fun largePayloadRequestChannel200() = test(TransportTestLongDuration) {
        val request = flow {
            repeat(200) { emit(LARGE_PAYLOAD) }
        }
        val list = client.requestChannel(request).buffer(Int.MAX_VALUE).onEach { it.release() }.toList()
        assertEquals(200, list.size)
    }

    @Test
    fun requestChannel20000() = test(TransportTestLongDuration) {
        val request = flow {
            repeat(20_000) { emit(payload(7)) }
        }
        val list = client.requestChannel(request).buffer(Int.MAX_VALUE).onEach {
            assertEquals(MOCK_DATA, it.data.readText())
            assertEquals(MOCK_METADATA, it.metadata?.readText())
        }.toList()
        assertEquals(20_000, list.size)
    }

    @Test
    fun requestChannel200000() = test(TransportTestLongDuration) {
        val request = flow {
            repeat(200_000) { emit(payload(it)) }
        }
        val list = client.requestChannel(request).buffer(Int.MAX_VALUE).onEach { it.release() }.toList()
        assertEquals(200_000, list.size)
    }

    @Test
    fun requestChannel256x512() = test(TransportTestLongDuration) {
        val request = flow {
            repeat(512) {
                emit(payload(it))
            }
        }
        (0..256).map {
            async(Dispatchers.Default) {
                val list = client.requestChannel(request).onEach { it.release() }.toList()
                assertEquals(512, list.size)
            }
        }.awaitAll()
    }

    @Test
    fun requestResponse1() = test {
        client.requestResponse(payload(1)).let(Companion::checkPayload)
    }

    @Test
    fun requestResponse10() = test {
        (1..10).map { async { client.requestResponse(payload(it)).let(Companion::checkPayload) } }.awaitAll()
    }

    @Test
    fun requestResponse100() = test {
        (1..100).map { async { client.requestResponse(payload(it)).let(Companion::checkPayload) } }.awaitAll()
    }

    @Test
    fun largePayloadRequestResponse100() = test {
        (1..100).map { async { client.requestResponse(LARGE_PAYLOAD) } }.awaitAll().onEach { it.release() }
    }

    @Test
    fun requestResponse10000() = test {
        (1..10000).map { async { client.requestResponse(payload(3)).let(Companion::checkPayload) } }.awaitAll()
    }

    @Test
    fun requestResponse100000() = test(TransportTestLongDuration) {
        repeat(100000) { client.requestResponse(payload(3)).let(Companion::checkPayload) }
    }

    @Test
    fun requestStream5() = test {
        val list = client.requestStream(payload(3)).buffer(5).take(5).onEach { checkPayload(it) }.toList()
        assertEquals(5, list.size)
    }

    @Test
    fun requestStream10000() = test {
        val list = client.requestStream(payload(3)).onEach { checkPayload(it) }.toList()
        assertEquals(10000, list.size)
    }

    companion object {

        val ACCEPTOR: RSocketAcceptor = { TestRSocket() }
        val CONNECTOR_CONFIG = RSocketConnectorConfiguration(keepAlive = KeepAlive(10.minutes, 100.minutes), loggerFactory = NoopLogger)
        val SERVER_CONFIG = RSocketServerConfiguration(loggerFactory = NoopLogger)

        val MOCK_DATA: String = "test-data"
        val MOCK_METADATA: String = "metadata"
        val LARGE_DATA = readLargePayload("words.shakespeare.txt.gz")
        private val payload = payload(LARGE_DATA, LARGE_DATA)
        val LARGE_PAYLOAD get() = payload.copy()

        private fun readLargePayload(name: String): String = name.repeat(1000)

        private fun payload(metadataPresent: Int): Payload {
            val metadata = when (metadataPresent % 5) {
                0 -> null
                1 -> ""
                else -> MOCK_METADATA
            }
            return payload(MOCK_DATA, metadata)
        }

        fun checkPayload(payload: Payload) {
            assertEquals(TestRSocket.data, payload.data.readText())
            assertEquals(TestRSocket.metadata, payload.metadata?.readText())
        }
    }
}
