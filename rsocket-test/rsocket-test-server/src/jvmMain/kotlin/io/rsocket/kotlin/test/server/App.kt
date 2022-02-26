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

package io.rsocket.kotlin.test.server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.ktor.websocket.server.*
import kotlinx.coroutines.*
import java.io.*

fun main() {
    start().await()
}

fun start(): TestServer {
    val server = TestServer()
    server.start()
    return server
}

class TestServer : Closeable {
    private val job = Job()
    private var wsServer: ApplicationEngine? = null
    private val rSocketServer = RSocketServer {
//        loggerFactory = PrintLogger.withLevel(LoggingLevel.DEBUG)
    }

    fun start(): Unit = runCatching {
        val scope = CoroutineScope(job)

        //start TCP server
        rSocketServer.bindIn(scope, TcpServerTransport(port = 8000)) { TestRSocket() }

        //start WS server
        wsServer = scope.embeddedServer(CIO, port = 9000) {
            install(WebSockets)
            install(RSocketSupport) { server = rSocketServer }

            routing {
                rSocket { TestRSocket() }
            }
        }.start()

        Thread.sleep(1000) //await start
    }.onFailure { close() }.getOrThrow()

    fun await() {
        runBlocking { job.join() }
    }

    override fun close() {
        runBlocking { job.cancelAndJoin() }
        wsServer?.stop(0, 1000)
    }
}
