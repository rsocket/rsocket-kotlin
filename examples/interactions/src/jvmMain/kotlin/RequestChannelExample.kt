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
import kotlinx.coroutines.flow.*


fun main(): Unit = runBlocking {
    val server = RSocketServer().bindIn(this, LocalServerTransport()) {
        RSocketRequestHandler {
            requestChannel { init, request ->
                println("Init with: ${init.data.readText()}")
                request.flowOn(PrefetchStrategy(3, 0)).take(3).flatMapConcat { payload ->
                    val data = payload.data.readText()
                    flow {
                        repeat(3) {
                            emit(Payload("$data(copy $it)"))
                        }
                    }
                }
            }
        }
    }
    val rSocket = RSocketConnector().connect(server)

    val request = flow {
        emit(Payload("Hello"))
        println("Client: Hello")
        emit(Payload("World"))
        println("Client: World")
        emit(Payload("Yes"))
        println("Client: Yes")
        emit(Payload("No"))
        println("Client: No") //no print
    }

    val response = rSocket.requestChannel(Payload("Init"), request)
    response.collect {
        val data = it.data.readText()
        println("Client receives: $data")
    }
}
