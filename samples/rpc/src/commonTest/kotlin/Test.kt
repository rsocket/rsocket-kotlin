/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.samples.rpc

import io.rsocket.kotlin.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.metadata.security.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import kotlin.test.*

@Serializable
class SetupData(
    val name: String
)

@Serializable
class TestRpcRequest(
    val id: Int
) : RpcRequest<TestRpcResponse>

@Serializable
class TestRpcResponse(
    val data: String
) : RpcResponse

@OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalMetadataApi::class,
    DelicateCoroutinesApi::class,
    ExperimentalCoroutinesApi::class
)
class RpcTest {
    /**
     * shows:
     *  - how to create rpc client and server
     *  - do rpc request from client
     *  - do rpc request from server
     *  - handle metadata pushing
     */
    @Test
    fun test() = runTest {
        val tokenUpdate = CompletableDeferred<String>()
        val server = RpcServer {
            protobuf(ProtoBuf)
        }.bind<LocalServer, SetupData>(LocalServerTransport()) {
            assertEquals("Oleg", setup.data.name)
            assertEquals("TOKEN", setup.metadata?.get(BearerAuthMetadata)?.token)

            //responder for client requests
            responder {
                onRequest<TestRpcRequest, TestRpcResponse> {
                    TestRpcResponse("Response for: ${it.id}")
                }

                onMetadataPush {
                    val newToken = it[BearerAuthMetadata].token
                    assertEquals("NEW TOKEN", newToken)

                    //do request to client
                    val result = requester
                        .stream(TestRpcRequest(2))
                        .withIndex()
                        .onEach { (index, response) ->
                            assertEquals("Response[2]: $index", response.data)
                        }
                        .toList()
                        .size
                    assertEquals(10, result)

                    tokenUpdate.complete(newToken)
                }
            }
        }

        val requester = RpcClient(ProtoBuf) {
            setup(SetupData("Oleg"), metadata = {
                add(BearerAuthMetadata("TOKEN"))
            }) {
                //responder for server requests
                responder {
                    onStream<TestRpcRequest, TestRpcResponse> { request ->
                        flow {
                            repeat(10) {
                                emit(TestRpcResponse("Response[${request.id}]: $it"))
                            }
                        }
                    }
                }
            }
        }.connect(server)

        //do requests to server
        requester.pushMetadata {
            add(BearerAuthMetadata("NEW TOKEN"))
        }

        assertEquals("Response for: 1", requester.request(TestRpcRequest(1)).data)

        assertEquals("NEW TOKEN", tokenUpdate.await())

        requester.coroutineContext.job.cancelAndJoin()
        server.coroutineContext.job.cancelAndJoin()
    }

    //decoding based on mime type - ProtoBuf vs Json
    @Test
    fun multiType() = runTest {
        val server = RpcServer {
            protobuf(ProtoBuf)
            json(Json)
        }.bind<LocalServer, String>(LocalServerTransport()) {
            println("SETUP: ${setup.data}")
            responder {
                onRequest<TestRpcRequest, TestRpcResponse> {
                    TestRpcResponse("Response[${setup.data}]: ${it.id}")
                }
            }
        }

        val requesterProtoBuf = RpcClient(ProtoBuf) {
            setup("ProtoBuf")
        }.connect(server)
        val requesterJson = RpcClient(Json) {
            setup("Json")
        }.connect(server)

        assertEquals("Response[ProtoBuf]: 1", requesterProtoBuf.request(TestRpcRequest(1)).data)
        assertEquals("Response[Json]: 1", requesterJson.request(TestRpcRequest(1)).data)


        requesterProtoBuf.coroutineContext.job.cancelAndJoin()
        requesterJson.coroutineContext.job.cancelAndJoin()
        server.coroutineContext.job.cancelAndJoin()
    }
}
