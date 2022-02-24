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

package io.rsocket.kotlin.transport.tests

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class TransportTest : SuspendTest, TestWithLeakCheck {
    override val testTimeout: Duration = 3.minutes

    protected val testJob = Job()
    protected val testContext = testJob + TestExceptionHandler
    protected val testScope = CoroutineScope(testContext)

    protected lateinit var client: RSocket

    protected suspend fun connectClient(clientTransport: ClientTransport): RSocket =
        CONNECTOR.connect(clientTransport)

    protected fun <T> startServer(serverTransport: ServerTransport<T>): T =
        SERVER.bindIn(testScope, serverTransport, ACCEPTOR)

    override suspend fun after() {
        client.coroutineContext.job.cancelAndJoin()
        testJob.cancelAndJoin()
    }

    @Test
    fun fireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(payload(it)) } }.awaitAll()
    }

    @Test
    fun largePayloadFireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(requesterLargeMetadata) } }.awaitAll()
    }

    @Test
    fun metadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(requesterData)) } }.awaitAll()
    }

    @Test
    fun largePayloadMetadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(requesterLargeData)) } }.awaitAll()
    }

    @Test
    fun requestChannel0() = test(10.seconds) {
        val list = client.requestChannel(payload(0), emptyFlow()).toList()
        assertTrue(list.isEmpty())
    }

    @Test
    fun requestChannel1() = test(10.seconds) {
        val list = client.requestChannel(payload(0), flowOf(payload(0))).onEach { it.close() }.toList()
        assertEquals(1, list.size)
    }

    @Test
    fun requestChannel3() = test {
        val request = flow {
            repeat(3) { emit(payload(it)) }
        }
        val list =
            client.requestChannel(payload(0), request).requestBy(3, 0).onEach { it.close() }.toList()
        assertEquals(3, list.size)
    }

    @Test
    fun largePayloadRequestChannel200() = test {
        val request = flow {
            repeat(200) { emit(requesterLargeMetadata) }
        }
        val list =
            client.requestChannel(requesterLargeMetadata, request)
                .requestAll()
                .onEach { it.close() }
                .toList()
        assertEquals(200, list.size)
    }

    @Test
    fun requestChannel20000() = test {
        val request = flow {
            repeat(20_000) { emit(payload(7)) }
        }
        val list = client.requestChannel(payload(7), request).requestAll().onEach {
            assertEquals(requesterData, it.data.readText())
            assertEquals(requesterMetadata, it.metadata?.readText())
        }.toList()
        assertEquals(20_000, list.size)
    }

    @Test
    @IgnoreNative //long test
    fun requestChannel200000() = test {
        val request = flow {
            repeat(200_000) { emit(payload(it)) }
        }
        val list =
            client.requestChannel(payload(0), request).requestBy(10000, 0).onEach { it.close() }.toList()
        assertEquals(200_000, list.size)
    }

    @Test
    fun requestChannel16x256() = test {
        val request = flow {
            repeat(256) {
                emit(payload(it))
            }
        }
        (0..16).map {
            async(Dispatchers.Default) {
                val list = client.requestChannel(payload(0), request).onEach { it.close() }.toList()
                assertEquals(256, list.size)
            }
        }.awaitAll()
    }

    @Test
    @IgnoreNative //long test
    fun requestChannel256x512() = test {
        val request = flow {
            repeat(512) {
                emit(payload(it))
            }
        }
        (0..256).map {
            async(Dispatchers.Default) {
                val list = client.requestChannel(payload(0), request).onEach { it.close() }.toList()
                assertEquals(512, list.size)
            }
        }.awaitAll()
    }

    @Test
    fun requestChannel500NoLeak() = test {
        val request = flow {
            repeat(10_000) { emitOrClose(payload(3)) }
        }
        val list =
            client
                .requestChannel(payload(3), request)
                .requestAll()
                .take(500)
                .onEach {
                    assertEquals(requesterData, it.data.readText())
                    assertEquals(requesterMetadata, it.metadata?.readText())
                }.toList()
        assertEquals(500, list.size)
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
        (1..100).map { async { client.requestResponse(requesterLargeMetadata) } }.awaitAll().onEach { it.close() }
    }

    @Test
    fun requestResponse10000() = test {
        (1..10000).map { async { client.requestResponse(payload(3)).let(Companion::checkPayload) } }.awaitAll()
    }

    @Test
    @IgnoreNative //long test
    fun requestResponse100000() = test {
        repeat(100000) { client.requestResponse(payload(3)).let(Companion::checkPayload) }
    }

    @Test
    fun requestStream5() = test {
        val list =
            client.requestStream(payload(3)).requestOnly(5).onEach { checkPayload(it) }.toList()
        assertEquals(5, list.size)
    }

    @Test
    fun requestStream10000() = test {
        val list = client.requestStream(payload(3)).onEach { checkPayload(it) }.toList()
        assertEquals(10000, list.size)
    }

    @Test
    fun requestStream500NoLeak() = test {
        val list =
            client
                .requestStream(payload(3))
                .requestAll()
                .take(500)
                .onEach { checkPayload(it) }
                .toList()
        assertEquals(500, list.size)
    }

    companion object {
        val SERVER = TestServer(logging = false)
        val CONNECTOR = TestConnector(logging = false) {
            connectionConfig {
                keepAlive = KeepAlive(10.minutes, 100.minutes)
            }
        }

        val ACCEPTOR = ConnectionAcceptor { ResponderRSocket() }

        const val responderData = "hello world"
        const val responderMetadata = "metadata"

        const val requesterData: String = "test-data"
        const val requesterMetadata: String = "metadata"

        val requesterLargeData = "large.text.12345".repeat(2000)
        val requesterLargeMetadata get() = payload(requesterLargeData, requesterLargeData)

        fun payload(metadataPresent: Int): Payload {
            val metadata = when (metadataPresent % 5) {
                0    -> null
                1    -> ""
                else -> requesterMetadata
            }
            return payload(requesterData, metadata)
        }

        fun checkPayload(payload: Payload) {
            assertEquals(responderData, payload.data.readText())
            assertEquals(responderMetadata, payload.metadata?.readText())
        }
    }

    private class ResponderRSocket : RSocket {
        override val coroutineContext: CoroutineContext = Job()

        override suspend fun metadataPush(metadata: ByteReadPacket): Unit = metadata.close()

        override suspend fun fireAndForget(payload: Payload): Unit = payload.close()

        override suspend fun requestResponse(payload: Payload): Payload {
            payload.close()
            return Payload(packet(responderData), packet(responderMetadata))
        }

        override fun requestStream(payload: Payload): Flow<Payload> = flow {
            payload.close()
            repeat(10000) {
                emitOrClose(Payload(packet(responderData), packet(responderMetadata)))
            }
        }

        override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = flow {
            initPayload.close()
            payloads.collect { emitOrClose(it) }
        }
    }

}
