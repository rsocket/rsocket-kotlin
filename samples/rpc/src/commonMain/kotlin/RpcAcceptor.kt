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
import kotlinx.serialization.*

fun interface RpcAcceptor<T> {
    suspend fun RpcAcceptorContext<T>.accept(): RSocket
}

class RpcAcceptorContext<T> internal constructor(
    private val codec: RpcCodec,
    val setup: RpcSetupConfig<T>,
    val requester: RpcRequester
) {
    fun responder(block: RpcResponderBuilder.() -> Unit): RSocket = RpcResponder(codec, block)
}

class RpcSetupConfig<T>
@OptIn(ExperimentalMetadataApi::class)
internal constructor(
    val keepAlive: KeepAlive,
    val dataMimeType: MimeType,
    val data: T,
    val metadata: CompositeMetadata?
)

@OptIn(ExperimentalMetadataApi::class)
internal fun <T> ConnectionAcceptor(
    codec: RpcCodec,
    serializer: KSerializer<T>,
    rpcAcceptor: RpcAcceptor<T>
): ConnectionAcceptor = ConnectionAcceptor {
    with(rpcAcceptor) {
        RpcAcceptorContext(
            codec = codec,
            setup = RpcSetupConfig(
                keepAlive = config.keepAlive,
                dataMimeType = WellKnownMimeType(config.payloadMimeType.data)
                    ?: CustomMimeType(config.payloadMimeType.data),
                data = codec.decodeData(serializer, config.setupPayload.data),
                metadata = config.setupPayload.metadata?.read(CompositeMetadata)
            ),
            requester = RpcRequester(requester, codec)
        ).accept()
    }
}
