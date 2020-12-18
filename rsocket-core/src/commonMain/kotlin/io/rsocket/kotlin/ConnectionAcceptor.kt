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

package io.rsocket.kotlin

import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*


//TODO fun interfaces don't support `suspend` functions for now... (seems will work in kotlin 1.5)

public interface ConnectionAcceptor {
    public suspend fun ConnectionAcceptorContext.accept(): RSocket
}

public inline fun ConnectionAcceptor(crossinline block: suspend ConnectionAcceptorContext.() -> RSocket): ConnectionAcceptor =
    object : ConnectionAcceptor {
        override suspend fun ConnectionAcceptorContext.accept(): RSocket = block()
    }

public class ConnectionAcceptorContext internal constructor(
    public val config: ConnectionConfig,
    public val requester: RSocket,
)

public class ConnectionConfig internal constructor(
    public val keepAlive: KeepAlive,
    public val payloadMimeType: PayloadMimeType,
    public val setupPayload: Payload,
)
