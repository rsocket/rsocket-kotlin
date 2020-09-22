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
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun main(): Unit = runBlocking {
    val (clientConnection, serverConnection) = SimpleLocalConnection()

    launch {
        serverConnection.startServer {
            RSocketRequestHandler {
                requestStream = {
                    val data = it.data.readText()
                    val metadata = it.metadata?.readText()
                    println("Server received payload: data=$data, metadata=$metadata")

                    flow {
                        repeat(10) { i ->
                            emit(Payload("Payload: $i", metadata))
                            println("Server sent: $i")
                        }
                    }
                }
            }
        }
    }

    val rSocket = clientConnection.connectClient()

    val response = rSocket.requestStream(Payload("Hello", "World"))

    response
        .buffer(2) //use buffer as first operator to use RequestN semantic
        .map { it.data.readText().substringAfter("Payload: ").toInt() }
        .take(2)
        .collect {
            println("Client receives index: $it")
        }
}
