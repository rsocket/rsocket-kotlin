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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlin.coroutines.*
import kotlin.reflect.*


@Suppress("FunctionName")
fun RpcResponder(codec: RpcCodec, block: RpcResponderBuilder.() -> Unit): RSocket =
    RpcResponderBuilder(codec).apply(block).build()

@OptIn(ExperimentalSerializationApi::class)
class RpcResponderBuilder internal constructor(private val codec: RpcCodec) {

    @ExperimentalMetadataApi
    private var metadataPushHandler: (suspend (CompositeMetadata) -> Unit)? = null

    private val fafHandlers = mutableMapOf<String, suspend (ByteReadPacket) -> Unit>()
    private val requestHandlers = mutableMapOf<String, suspend (ByteReadPacket) -> Payload>()
    private val streamHandlers = mutableMapOf<String, (ByteReadPacket) -> Flow<Payload>>()
    private val channelHandlers = mutableMapOf<String, (Flow<ByteReadPacket>) -> Flow<Payload>>()

    @ExperimentalMetadataApi
    fun onMetadataPush(handler: suspend (CompositeMetadata) -> Unit) {
        check(metadataPushHandler == null) { "Metadata push handler already set" }
        metadataPushHandler = handler
    }

    inline fun <reified Req : FafRpcRequest> onFaf(
        noinline handler: suspend (request: Req) -> Unit
    ) {
        onFaf(typeOf<Req>(), handler)
    }

    inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> onRequest(
        noinline handler: suspend (request: Req) -> Res
    ) {
        onRequest(typeOf<Req>(), typeOf<Res>(), handler)
    }

    inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> onStream(
        noinline handler: suspend (request: Req) -> Flow<Res>
    ) {
        onStream(typeOf<Req>(), typeOf<Res>(), handler)
    }

    inline fun <reified Req : RpcRequest<Res>, reified Res : RpcResponse> onChannel(
        noinline handler: suspend (requestFlow: Flow<Req>) -> Flow<Res>
    ) {
        onChannel(typeOf<Req>(), typeOf<Res>(), handler)
    }

    @PublishedApi
    internal fun <Req : FafRpcRequest> onFaf(
        requestType: KType,
        handler: suspend (request: Req) -> Unit
    ) {
        val requestSerializer = codec.serializer<Req>(requestType)
        val route = requestSerializer.descriptor.serialName

        check(fafHandlers[route] == null) { "Faf Handler for route `$route` already set" }

        fafHandlers[route] = { data ->
            handler(
                codec.decodeData(requestSerializer, data)
            )
        }
    }


    @PublishedApi
    internal fun <Req : RpcRequest<Res>, Res : RpcResponse> onRequest(
        requestType: KType,
        responseType: KType,
        handler: suspend (request: Req) -> Res
    ) {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        val route = requestSerializer.descriptor.serialName

        check(requestHandlers[route] == null) { "Request Handler for route `$route` already set" }

        requestHandlers[route] = { data ->
            val response = handler(
                codec.decodeData(requestSerializer, data)
            )
            codec.encodeResponsePayload(responseSerializer, response)
        }
    }

    @PublishedApi
    internal fun <Req : RpcRequest<Res>, Res : RpcResponse> onStream(
        requestType: KType,
        responseType: KType,
        handler: suspend (request: Req) -> Flow<Res>
    ) {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        val route = requestSerializer.descriptor.serialName

        check(streamHandlers[route] == null) { "Stream Handler for route `$route` already set" }

        streamHandlers[route] = { data ->
            flow {
                handler(
                    codec.decodeData(requestSerializer, data)
                ).collect {
                    emit(codec.encodeResponsePayload(responseSerializer, it))
                }
            }
        }
    }

    @PublishedApi
    internal fun <Req : RpcRequest<Res>, Res : RpcResponse> onChannel(
        requestType: KType,
        responseType: KType,
        handler: suspend (requestFlow: Flow<Req>) -> Flow<Res>
    ) {
        val requestSerializer = codec.serializer<Req>(requestType)
        val responseSerializer = codec.serializer<Res>(responseType)

        val route = requestSerializer.descriptor.serialName

        check(channelHandlers[route] == null) { "Channel Handler for route `$route` already set" }

        channelHandlers[route] = { dataFlow ->
            flow {
                handler(
                    dataFlow.map { codec.decodeData(requestSerializer, it) }
                ).collect {
                    emit(codec.encodeResponsePayload(responseSerializer, it))
                }
            }
        }
    }

    @OptIn(ExperimentalMetadataApi::class)
    internal fun build(): RSocket = RpcResponder(
        metadataPushHandler,
        fafHandlers,
        requestHandlers,
        streamHandlers,
        channelHandlers
    )
}

@OptIn(ExperimentalMetadataApi::class)
private class RpcResponder(
    private var metadataPushHandler: (suspend (CompositeMetadata) -> Unit)?,
    private val fafHandlers: Map<String, suspend (ByteReadPacket) -> Unit>,
    private val requestHandlers: Map<String, suspend (ByteReadPacket) -> Payload>,
    private val streamHandlers: Map<String, (ByteReadPacket) -> Flow<Payload>>,
    private val channelHandlers: Map<String, (Flow<ByteReadPacket>) -> Flow<Payload>>,
) : RSocket {
    override val coroutineContext: CoroutineContext = Job()

    private fun Payload.route(): String {
        return metadata
            ?.read(CompositeMetadata)
            ?.get(RoutingMetadata)
            ?.tags
            ?.firstOrNull()
            ?: error("No routing info")
    }

    override suspend fun metadataPush(metadata: ByteReadPacket) {
        metadataPushHandler?.invoke(metadata.read(CompositeMetadata)) ?: error("No Metadata Push handler")
    }

    override suspend fun fireAndForget(payload: Payload) {
        val route = payload.route()
        return fafHandlers[route]
            ?.invoke(payload.data)
            ?: error("No Faf handler for route $route")
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        val route = payload.route()
        return requestHandlers[route]
            ?.invoke(payload.data)
            ?: error("No Request handler for route $route")
    }

    override fun requestStream(payload: Payload): Flow<Payload> {
        val route = payload.route()
        return streamHandlers[route]
            ?.invoke(payload.data)
            ?: error("No Stream handler for route $route")
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        val route = initPayload.route()
        return channelHandlers[route]
            ?.invoke(
                payloads.map {
                    it.metadata?.close()
                    it.data
                }.onStart { emit(initPayload.data) }
            )
            ?: error("No Channel handler for route $route")
    }
}
