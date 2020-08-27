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

package io.rsocket.kotlin.benchmarks

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.random.*

class RSocketKotlinBenchmark : RSocketBenchmark<Payload>() {

    lateinit var client: RSocket
    lateinit var server: Job

    lateinit var payload: Payload
    lateinit var payloadsFlow: RequestingFlow<Payload>

    override fun setup() {
        payload = createPayload(payloadSize)
        payloadsFlow = flow { repeat(5000) { emit(payload.copy()) } }.onRequest()

        val clientChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
        val serverConnection = LocalConnection("server", clientChannel, serverChannel)
        val clientConnection = LocalConnection("client", serverChannel, clientChannel)

        client = runBlocking {
            launch {
                server = RSocketServer(ConnectionProvider(serverConnection)).start {
                    RSocketRequestHandler {
                        requestResponse = {
                            it.release()
                            payload
                        }
                        requestStream = {
                            it.release()
                            payloadsFlow
                        }
                        requestChannel = { it.onRequest() }
                    }
                }
            }
            RSocketConnector(ConnectionProvider(clientConnection)).connect()
        }
    }

    override fun cleanup() {
        runBlocking {
            client.job.runCatching { cancelAndJoin() }
            server.runCatching { cancelAndJoin() }
        }
    }

    override fun createPayload(size: Int): Payload = if (size == 0) Payload.Empty else Payload(
        ByteArray(size / 2).also { Random.nextBytes(it) },
        ByteArray(size / 2).also { Random.nextBytes(it) }
    )

    override fun releasePayload(payload: Payload) {
        payload.release()
    }

    override suspend fun doRequestResponse(): Payload = client.requestResponse(payload.copy())

    override suspend fun doRequestStream(): Flow<Payload> = client.requestStream(payload.copy())

    override suspend fun doRequestChannel(): Flow<Payload> = client.requestChannel(payloadsFlow)

}
