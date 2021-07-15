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

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.ktor.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

suspend fun runTcpServer(dispatcher: CoroutineContext) {
    val transport = TcpServerTransport("0.0.0.0", 4444)
    RSocketServer().bindIn(CoroutineScope(dispatcher), transport, rSocketAcceptor).handlerJob.join()
}

suspend fun main(): Unit = runTcpServer(Dispatchers.IO)
