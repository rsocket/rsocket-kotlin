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
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*

fun main(): Unit = runBlocking {
    val server = LocalServer()
    RSocketServer().bind(server) {
        RSocketRequestHandler {
            requestResponse {
                val data = it.data.readText()
                val metadata = it.metadata?.readText()
                println("Server received payload: data=$data, metadata=$metadata")

                Payload(data.repeat(3), "SERVER_METADATA") //response
            }
        }
    }
    val rSocket = RSocketConnector().connect(server)

    val response = rSocket.requestResponse(Payload("Hello", "World"))

    val data = response.data.readText()
    val metadata = response.metadata?.readText()

    println("Client receives payload: data=$data, metadata=$metadata")
}
