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
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*

fun main(): Unit = runBlocking {
    val server = LocalServer()
    RSocketServer().bind(server) {
        RSocketRequestHandler {
            requestResponse {
                val clientRequest = it.data.readText()
                println("Server got from client request: $clientRequest")
                val response = requester.requestResponse(Payload("What happens?"))
                val clientResponse = response.data.readText()
                println("Server got from client response: $clientResponse")
                Payload("I'm frustrated because of `$clientResponse`")
            }
        }
    }
    val rSocket = RSocketConnector {
        acceptor {
            RSocketRequestHandler {
                requestResponse {
                    val serverRequest = it.data.readText()
                    println("Client got from server request: $serverRequest")
                    Payload("I'm client!")
                }
            }
        }
    }.connect(server)

    val response = rSocket.requestResponse(Payload("How are you server?"))
    val data = response.data.readText()
    println("Result: $data")
}
