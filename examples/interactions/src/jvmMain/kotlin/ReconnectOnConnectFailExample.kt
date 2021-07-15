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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@TransportApi
fun main(): Unit = runBlocking {
    val server = RSocketServer().bindIn(this, LocalServerTransport()) {
        RSocketRequestHandler {
            requestStream { requestPayload ->
                val data = requestPayload.data.readText()
                val metadata = requestPayload.metadata?.readText()
                println("Server received payload: data=$data, metadata=$metadata")

                flow {
                    repeat(50) { i ->
                        delay(100)
                        emit(Payload("Payload: $i", metadata))
                        println("Server sent: $i")
                    }
                }
            }
        }
    }

    //emulate connection establishment error
    var first = true

    val rSocket = RSocketConnector {
        //reconnect 10 times with 1 second delay if connection establishment failed
        reconnectable(10) {
            delay(1000)
            println("Retry after error: $it")
            true
        }
    }.connect(ClientTransport {
        if (first) {
            first = false
            error("Connection establishment failed") //emulate connection establishment error
        }
        server.connect()
    })

    //do request
    rSocket.requestStream(Payload("Hello", "World")).flowOn(PrefetchStrategy(3, 0)).take(3).collect {
        val index = it.data.readText().substringAfter("Payload: ").toInt()
        println("Client receives index: $index")
    }

}
