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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


fun main(): Unit = runBlocking {

    val acceptor: RSocketAcceptor = {
        val data = payload.metadata?.readText() ?: error("Empty metadata")
        RSocketRequestHandler {
            when (data) {
                "rr" -> requestResponse = {
                    println("Server receive: ${it.data.readText()}")
                    Payload("From server")
                }
                "rs" -> requestStream = {
                    println("Server receive: ${it.data.readText()}")
                    flowOf(Payload("From server"))
                }
            }
        }
    }

    suspend fun client1() {

        val (clientConnection, serverConnection) = SimpleLocalConnection()

        GlobalScope.launch {
            serverConnection.startServer(acceptor = acceptor)
        }

        val rSocketClient = clientConnection.connectClient()
        rSocketClient.job.join()
        println("Client 1 canceled: ${rSocketClient.job.isCancelled}")
        try {
            rSocketClient.requestResponse(Payload.Empty)
        } catch (e: Throwable) {
            println("Client 1 canceled after creation with: $e")
        }
    }

    suspend fun client2() {

        val (clientConnection, serverConnection) = SimpleLocalConnection()

        GlobalScope.launch {
            serverConnection.startServer(acceptor = acceptor)
        }

        val rSocketClient = clientConnection.connectClient(
            RSocketConnectorConfiguration(setupPayload = Payload("", "rr"))
        )

        val payload = rSocketClient.requestResponse(Payload("2"))
        println("Client 2 receives: ${payload.data.readText()}")
        try {
            rSocketClient.requestStream(Payload.Empty).collect()
        } catch (e: Throwable) {
            println("Client 2 receives error: ${e.message}")
        }
    }

    suspend fun client3() {

        val (clientConnection, serverConnection) = SimpleLocalConnection()

        GlobalScope.launch {
            serverConnection.startServer(acceptor = acceptor)
        }

        val rSocketClient = clientConnection.connectClient(
            RSocketConnectorConfiguration(setupPayload = Payload("", "rs"))
        )
        val payloads = rSocketClient.requestStream(Payload("3")).toList()
        println("Client 3 receives: ${payloads.map { it.data.readText() }}")
        try {
            rSocketClient.requestResponse(Payload.Empty)
        } catch (e: Throwable) {
            println("Client 3 receives error: ${e.message}")
        }
    }

    client1()
    client2()
    client3()

}
