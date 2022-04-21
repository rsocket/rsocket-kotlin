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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import kotlin.reflect.*

class RpcClient internal constructor(
    private val connector: RSocketConnector,
    private val codec: RpcCodec
) {
    suspend fun connect(transport: ClientTransport): RpcRequester {
        val rSocket = connector.connect(transport)
        return RpcRequester(rSocket, codec)
    }
}

fun RpcClient(
    json: Json,
    block: RpcClientBuilder.() -> Unit = {}
): RpcClient =
    RpcClientBuilder(PacketFormat.String(json), WellKnownMimeType.ApplicationJson).apply(block).build()

@ExperimentalSerializationApi
fun RpcClient(
    protoBuf: ProtoBuf,
    block: RpcClientBuilder.() -> Unit = {}
): RpcClient =
    RpcClientBuilder(PacketFormat.Binary(protoBuf), WellKnownMimeType.ApplicationProtoBuf).apply(block).build()

fun RpcClient(
    format: PacketFormat,
    mimeType: MimeTypeWithName,
    block: RpcClientBuilder.() -> Unit = {}
): RpcClient = RpcClientBuilder(format, mimeType).apply(block).build()

class RpcClientBuilder internal constructor(
    format: PacketFormat,
    private val dataMimeType: MimeTypeWithName
) {
    private val codec = RpcCodec(format)

    private var connectorOverride: (RSocketConnectorBuilder.() -> Unit)? = null
    private var keepAlive: KeepAlive? = null
    private var setupPayload: (() -> Payload)? = null
    private var acceptor: ConnectionAcceptor? = null

    fun connector(block: (RSocketConnectorBuilder.() -> Unit)?) {
        this.connectorOverride = block
    }

    fun keepAlive(keepAlive: KeepAlive) {
        this.keepAlive = keepAlive
    }

    @OptIn(ExperimentalMetadataApi::class)
    inline fun <reified T> setup(
        data: T,
        noinline metadata: CompositeMetadataBuilder.() -> Unit = {},
        acceptor: RpcAcceptor<T>? = null
    ) {
        setup(typeOf<T>(), data, metadata, acceptor)
    }

    @OptIn(ExperimentalMetadataApi::class)
    @PublishedApi
    internal fun <T> setup(
        dataType: KType,
        data: T,
        metadata: CompositeMetadataBuilder.() -> Unit = {},
        acceptor: RpcAcceptor<T>? = null
    ) {
        val serializer = codec.serializer<T>(dataType)
        this.setupPayload = {
            buildPayload {
                data(codec.encodeData(serializer, data))
                compositeMetadata(metadata)
            }
        }
        this.acceptor = acceptor?.let { ConnectionAcceptor(codec, serializer, it) }
    }

    internal fun build(): RpcClient {
        val connector = RSocketConnector {
            connectionConfig {
                this.payloadMimeType = PayloadMimeType(
                    data = dataMimeType,
                    metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                )
                this@RpcClientBuilder.keepAlive?.let { this.keepAlive = it }
                setupPayload(this@RpcClientBuilder.setupPayload)
            }
            acceptor(acceptor)
            connectorOverride?.invoke(this)
        }
        return RpcClient(connector, codec)
    }
}
