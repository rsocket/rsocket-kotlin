/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use NodejsTcpClientTransport")
public class TcpClientTransport(
    private val port: Int,
    private val hostname: String,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : ClientTransport {

    override val coroutineContext: CoroutineContext = coroutineContext + SupervisorJob(coroutineContext[Job])

    override suspend fun connect(): Connection {
        val socket = connect(port, hostname)
        return TcpConnection(coroutineContext, socket)
    }
}
