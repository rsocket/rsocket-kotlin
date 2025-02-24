/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport.netty.quic

import io.netty.util.*
import io.rsocket.kotlin.transport.*
import kotlin.coroutines.*

@RSocketTransportApi
internal val ATTRIBUTE_STREAM: AttributeKey<NettyQuicStream> =
    AttributeKey.newInstance("rsocket-quic-stream")

@RSocketTransportApi
internal val ATTRIBUTE_CONNECTION: AttributeKey<NettyQuicConnection> =
    AttributeKey.newInstance("rsocket-quic-connection")

@RSocketTransportApi
internal val ATTRIBUTE_CONNECTION_INITIALIZER: AttributeKey<RSocketConnectionInitializer<Unit>> =
    AttributeKey.newInstance("rsocket-quic-connection-initializer")

@RSocketTransportApi
internal val ATTRIBUTE_TRANSPORT_CONTEXT: AttributeKey<CoroutineContext> =
    AttributeKey.newInstance("rsocket-quic-transport-context")
