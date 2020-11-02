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

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.ktor.*
import kotlin.coroutines.*


@OptIn(KtorExperimentalAPI::class, InternalAPI::class)
suspend fun runTcpClient(dispatcher: CoroutineContext) {
    val transport = aSocket(SelectorManager(dispatcher)).tcp().clientTransport("0.0.0.0", 4444)
    RSocketConnector().connect(transport).doSomething()
}

//to test nodejs tcp server
@OptIn(KtorExperimentalAPI::class, InternalAPI::class)
suspend fun testNodeJsServer(dispatcher: CoroutineContext) {
    val transport = aSocket(SelectorManager(dispatcher)).tcp().clientTransport("127.0.0.1", 9000)
    val client = RSocketConnector().connect(transport)

    val response = client.requestResponse(buildPayload { data("Hello from JVM") })
    println(response.data.readText())
}
