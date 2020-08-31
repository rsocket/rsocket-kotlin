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

package io.rsocket.kotlin

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.kotlin.connection.*
import kotlinx.coroutines.*
import kotlin.test.*

@OptIn(InternalAPI::class)
class TcpTransportTest : TransportTest() {
    private val selector = SelectorManager(Dispatchers.IO)
    private val builder = aSocket(selector).tcp()
    private lateinit var server: ServerSocket

    @BeforeTest
    fun setup() {
        server = runBlocking {
            trySeveralTimes {
                builder.bind("127.0.0.1", 2323)
            }
        }
        GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
            server.accept().connection.startServer(SERVER_CONFIG, ACCEPTOR).join()
        }
    }

    @AfterTest
    fun cleanup() {
        server.close()
        selector.close()
        runBlocking { server.socketContext.join() }
    }

    override suspend fun init(): RSocket = trySeveralTimes {
        builder.connect("127.0.0.1", 2323).connection.connectClient(CONNECTOR_CONFIG)
    }

    private suspend inline fun <R> trySeveralTimes(block: () -> R): R {
        lateinit var error: Throwable
        repeat(5) {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
                delay(500) //sometimes address isn't yet available
            }
        }
        throw error
    }

}