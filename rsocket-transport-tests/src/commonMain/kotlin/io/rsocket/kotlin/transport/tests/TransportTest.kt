/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

//TODO: need to somehow rework those tests, as now they are super flaky
abstract class TransportTest : SuspendTest {
    override val testTimeout: Duration = 10.minutes

    private val testJob = SupervisorJob()
    protected val testContext = testJob + TestExceptionHandler
    protected val testScope = CoroutineScope(testContext)

    protected lateinit var client: RSocket

    @Suppress("DEPRECATION_ERROR")
    protected suspend fun connectClient(clientTransport: ClientTransport): RSocket =
        CONNECTOR.connect(clientTransport)

    @Suppress("DEPRECATION_ERROR")
    protected fun <T> startServer(serverTransport: ServerTransport<T>): T =
        SERVER.bindIn(testScope, serverTransport, ACCEPTOR)

    protected suspend fun connectClient(clientTransport: RSocketClientTarget): RSocket =
        CONNECTOR.connect(clientTransport)

    protected suspend fun <T : RSocketServerInstance> startServer(serverTransport: RSocketServerTarget<T>): T =
        SERVER.startServer(serverTransport, ACCEPTOR)

    override suspend fun after() {
        // TODO: we do need delays in FAF and MP tests because in reality, here, we don't wait for the connection to be completed
        //  and so we start to close connection from both ends simultaneously
        client.coroutineContext.job.cancelAndJoin()
        testJob.cancelAndJoin()
    }

    @Test
    fun fireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(payload(it)) } }.awaitAll()
        delay(100)
    }

    @Test
    open fun largePayloadFireAndForget10() = test {
        (1..10).map { async { client.fireAndForget(requesterLargePayload) } }.awaitAll()
        delay(100)
    }

    @Test
    fun metadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(requesterData)) } }.awaitAll()
        delay(100)
    }

    @Test
    open fun largePayloadMetadataPush10() = test {
        (1..10).map { async { client.metadataPush(packet(requesterLargeData)) } }.awaitAll()
        delay(100)
    }

    @Test
    fun requestChannel0() = test(10.seconds) {
        val list = client.requestChannel(payload(0), emptyFlow()).toList()
        assertTrue(list.isEmpty())
    }

    @Test
    fun requestChannel1() = test(10.seconds) {
        val count =
            client.requestChannel(payload(0), flowOf(payload(0)))
                .onEach { it.close() }
                .count()
        assertEquals(1, count)
    }

    @Test
    fun requestChannel3() = test {
        val request = flow {
            repeat(3) { emit(payload(it)) }
        }
        val count =
            client.requestChannel(payload(0), request)
                .flowOn(PrefetchStrategy(3, 0))
                .onEach { it.close() }
                .count()
        assertEquals(3, count)
    }

    @Test
    open fun largePayloadRequestChannel200() = test {
        val request = flow {
            repeat(200) { emit(requesterLargePayload) }
        }
        val count =
            client.requestChannel(requesterLargePayload, request)
                .flowOn(PrefetchStrategy(Int.MAX_VALUE, 0))
                .onEach { it.close() }
                .count()
        assertEquals(200, count)
    }

    @Test
    fun requestChannel20000() = test {
        val request = flow {
            repeat(20_000) { emit(payload(7)) }
        }
        val count = client.requestChannel(payload(7), request).flowOn(PrefetchStrategy(Int.MAX_VALUE, 0)).onEach {
            assertEquals(requesterData, it.data.readString())
            assertEquals(requesterMetadata, it.metadata?.readString())
        }.count()
        assertEquals(20_000, count)
    }

    @Test
    fun requestChannel200000() = test {
        val request = flow {
            repeat(200_000) { emit(payload(it)) }
        }
        val count =
            client.requestChannel(payload(0), request)
                .flowOn(PrefetchStrategy(10000, 0))
                .onEach { it.close() }
                .count()
        assertEquals(200_000, count)
    }

    @Test
    fun requestChannel16x256() = test {
        val request = flow {
            repeat(256) {
                emit(payload(it))
            }
        }
        (0..16).map {
            async {
                val count = client.requestChannel(payload(0), request).onEach { it.close() }.count()
                assertEquals(256, count)
            }
        }.awaitAll()
    }

    @Test
    fun requestChannel256x512() = test {
        val request = flow {
            repeat(512) {
                emit(payload(it))
            }
        }
        (0..256).map {
            async {
                val count = client.requestChannel(payload(0), request).onEach { it.close() }.count()
                assertEquals(512, count)
            }
        }.awaitAll()
    }

    @Test
    fun requestStreamX16() = test {
        (0..16).map {
            async {
                val count = client.requestStream(payload(0)).onEach { it.close() }.count()
                assertEquals(8192, count)
            }
        }.awaitAll()
    }

    @Test
    fun requestChannel500NoLeak() = test {
        val request = flow {
            repeat(10_000) { emitOrClose(payload(3)) }
        }
        val count =
            client
                .requestChannel(payload(3), request)
                .flowOn(PrefetchStrategy(Int.MAX_VALUE, 0))
                .take(500)
                .onEach {
                    assertEquals(requesterData, it.data.readString())
                    assertEquals(requesterMetadata, it.metadata?.readString())
                }
                .count()
        assertEquals(500, count)
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
    open fun largePayloadRequestResponse100() = test {
        (1..100).map { async { client.requestResponse(requesterLargePayload) } }.awaitAll().onEach { it.close() }
    }

    @Test
    fun requestResponse10000Sequential() = test {
        repeat(10000) {
            client.requestResponse(payload(3)).let(Companion::checkPayload)
        }
    }

    @Test
    fun requestResponse10000Parallel() = test {
        repeat(10000) {
            launch { client.requestResponse(payload(3)).let(Companion::checkPayload) }
        }
    }

    @Test
    fun requestStream5() = test {
        val count =
            client.requestStream(payload(3)).flowOn(PrefetchStrategy(5, 0)).take(5).onEach { checkPayload(it) }.count()
        assertEquals(5, count)
    }

    @Test
    fun requestStream8K() = test {
        val count = client.requestStream(payload(3)).onEach { checkPayload(it) }.count()
        assertEquals(8192, count)
    }

    @Test
    fun requestStream500NoLeak() = test {
        val count =
            client
                .requestStream(payload(3))
                .flowOn(PrefetchStrategy(Int.MAX_VALUE, 0))
                .take(500)
                .onEach { checkPayload(it) }
                .count()
        assertEquals(500, count)
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
        val requesterLargePayload get() = payload(requesterLargeData, requesterLargeData)

        fun payload(metadataPresent: Int): Payload {
            val metadata = when (metadataPresent % 5) {
                0    -> null
                1    -> ""
                else -> requesterMetadata
            }
            return payload(requesterData, metadata)
        }

        fun checkPayload(payload: Payload) {
            assertEquals(responderData, payload.data.readString())
            assertEquals(responderMetadata, payload.metadata?.readString())
        }
    }

    private class ResponderRSocket : RSocket {
        override val coroutineContext: CoroutineContext = Job()

        override suspend fun metadataPush(metadata: Buffer): Unit = metadata.close()

        override suspend fun fireAndForget(payload: Payload): Unit = payload.close()

        override suspend fun requestResponse(payload: Payload): Payload {
            payload.close()
            return Payload(packet(responderData), packet(responderMetadata))
        }

        override fun requestStream(payload: Payload): Flow<Payload> = flow {
            payload.close()
            repeat(8192) {
                emitOrClose(Payload(packet(responderData), packet(responderMetadata)))
            }
        }

        override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = flow {
            initPayload.close()
            payloads.collect { emitOrClose(it) }
        }
    }

}
