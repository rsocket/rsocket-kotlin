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

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import kotlin.reflect.*

class RpcServer internal constructor(
    private val server: RSocketServer,
    private val codecs: Map<String, RpcCodec>
) {
    @DelicateCoroutinesApi
    inline fun <S, reified T> bind(
        transport: ServerTransport<S>,
        acceptor: RpcAcceptor<T>
    ): S = bindIn(GlobalScope, transport, acceptor)

    inline fun <S, reified T> bindIn(
        scope: CoroutineScope,
        transport: ServerTransport<S>,
        acceptor: RpcAcceptor<T>
    ): S = bindIn(typeOf<T>(), scope, transport, acceptor)

    @PublishedApi
    internal fun <S, T> bindIn(
        type: KType,
        scope: CoroutineScope,
        transport: ServerTransport<S>,
        acceptor: RpcAcceptor<T>
    ): S = server.bindIn(scope, transport) {
        val dataMimeType = config.payloadMimeType.data
        val codec = codecs[dataMimeType] ?: error("Unsupported mime type: $dataMimeType")
        with(ConnectionAcceptor(codec, codec.serializer(type), acceptor)) { accept() }
    }
}

fun RpcServer(block: RpcServerBuilder.() -> Unit): RpcServer =
    RpcServerBuilder().apply(block).build()

class RpcServerBuilder internal constructor() {

    private var serverOverride: (RSocketServerBuilder.() -> Unit)? = null
    private val codecs = mutableMapOf<String, RpcCodec>()

    fun server(block: (RSocketServerBuilder.() -> Unit)?) {
        this.serverOverride = block
    }

    fun json(json: Json) {
        format(PacketFormat.String(json), WellKnownMimeType.ApplicationJson)
    }

    @ExperimentalSerializationApi
    fun protobuf(protoBuf: ProtoBuf) {
        format(PacketFormat.Binary(protoBuf), WellKnownMimeType.ApplicationProtoBuf)
    }

    fun format(
        format: PacketFormat,
        mimeType: MimeTypeWithName,
    ) {
        codecs[mimeType.text] = RpcCodec(format)
    }

    fun build(): RpcServer {
        val server = RSocketServer {
            serverOverride?.invoke(this)
        }
        return RpcServer(server, codecs)
    }

}