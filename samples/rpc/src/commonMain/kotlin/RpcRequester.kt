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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.*

class RpcRequester(
    private val rSocket: RSocket,
    private val codec: RpcCodec
) : CoroutineScope by rSocket {

    @ExperimentalMetadataApi
    suspend fun pushMetadata(metadata: CompositeMetadata) {
        rSocket.metadataPush(metadata.toPacket())
    }

    @ExperimentalMetadataApi
    suspend inline fun pushMetadata(block: CompositeMetadataBuilder.() -> Unit) {
        pushMetadata(buildCompositeMetadata(block))
    }

    suspend inline fun <reified Req : FafRpcRequest> faf(request: Req): Unit =
        faf(request, typeOf<Req>())

    suspend inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> request(request: Req): Res =
        request(request, typeOf<Req>(), typeOf<Res>())

    inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> stream(request: Req): Flow<Res> =
        stream(request, typeOf<Req>(), typeOf<Res>())

    inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> channel(requestFlow: Flow<Req>): Flow<Res> =
        channel(requestFlow, typeOf<Req>(), typeOf<Res>())

    @PublishedApi
    internal suspend fun <Req : FafRpcRequest> faf(
        request: Req,
        requestType: KType,
    ) {
        val requestSerializer = codec.serializer<Req>(requestType)

        rSocket.fireAndForget(
            codec.encodeInitRequestPayload(requestSerializer, request)
        )
    }

    @PublishedApi
    internal suspend fun <Req : RpcRequest<Res>, Res : RpcResponse> request(
        request: Req,
        requestType: KType,
        responseType: KType
    ): Res {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        val responsePayload = rSocket.requestResponse(
            codec.encodeInitRequestPayload(requestSerializer, request)
        )
        return codec.decodeResponsePayload(responseSerializer, responsePayload)
    }

    @PublishedApi
    internal fun <Req : RpcRequest<Res>, Res : RpcResponse> stream(
        request: Req,
        requestType: KType,
        responseType: KType
    ): Flow<Res> {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        return flow {
            rSocket.requestStream(
                codec.encodeInitRequestPayload(requestSerializer, request)
            ).collect {
                emit(codec.decodeResponsePayload(responseSerializer, it))
            }
        }
    }

    @OptIn(FlowPreview::class)
    @PublishedApi
    internal fun <Req : RpcRequest<Res>, Res : RpcResponse> channel(
        requestFlow: Flow<Req>,
        requestType: KType,
        responseType: KType
    ): Flow<Res> {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        return flow {
            coroutineScope {
                val requestChannel = requestFlow.buffer(capacity = Channel.RENDEZVOUS).produceIn(this)
                val initRequest = requestChannel.receive()
                rSocket.requestChannel(
                    codec.encodeInitRequestPayload(requestSerializer, initRequest),
                    requestChannel.consumeAsFlow().map { codec.encodeRequestPayload(requestSerializer, it) }
                ).collect {
                    emit(codec.decodeResponsePayload(responseSerializer, it))
                }
            }
        }
    }
}