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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.atomicfu.*
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

    val rSocket = RSocketConnector {
        //reconnect 10 times with 1 second delay if connection establishment failed
        reconnectable(10) {
            delay(1000)
            true
        }
    }.connect(ClientTransport {
        val connection = DisconnectableConnection(server.connect())
        launch {
            delay(500)
            connection.disconnect() //emulate connection fail
        }
        connection
    })

    //do request
    try {
        rSocket.requestStream(Payload("Hello", "World")).flowOn(PrefetchStrategy(3, 0)).collect {
            val index = it.data.readText().substringAfter("Payload: ").toInt()
            println("Client receives index: $index")
        }
    } catch (e: Throwable) {
        println("Request failed with error: $e")
    }

    //do request just after it

    rSocket.requestStream(Payload("Hello", "World")).flowOn(PrefetchStrategy(3, 0)).take(3).collect {
        val index = it.data.readText().substringAfter("Payload: ").toInt()
        println("Client receives index: $index after reconnection")
    }

}

@TransportApi
private class DisconnectableConnection(
    private val connection: Connection,
) : Connection by connection {
    private val disconnected = atomic(false)

    fun disconnect() {
        disconnected.value = true
    }

    override suspend fun send(packet: ByteReadPacket) {
        if (disconnected.value) error("Disconnected")
        connection.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        if (disconnected.value) error("Disconnected")
        return connection.receive()
    }
}
