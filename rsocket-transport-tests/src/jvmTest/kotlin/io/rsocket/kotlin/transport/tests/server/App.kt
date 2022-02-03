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

package io.rsocket.kotlin.transport.tests.server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.ktor.websocket.server.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*
import java.io.*

fun start(): Closeable {
    val job = Job()
    val scope = CoroutineScope(job)

    runBlocking {
        TransportTest.SERVER.bindIn(
            scope,
            TcpServerTransport(port = PortProvider.testServerTcp),
            TransportTest.ACCEPTOR
        ).serverSocket.await() //await server start
    }

    scope.embeddedServer(CIO, port = PortProvider.testServerWebSocket) {
        install(WebSockets)
        install(RSocketSupport) { server = TransportTest.SERVER }
        install(Routing) { rSocket(acceptor = TransportTest.ACCEPTOR) }
    }.start()

    Thread.sleep(1000) //await start

    return Closeable {
        runBlocking {
            job.cancelAndJoin()
        }
    }
}
