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

package tcp

import doSomething
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.connection.*
import kotlinx.coroutines.*
import java.util.concurrent.*

@OptIn(KtorExperimentalAPI::class)
fun main(): Unit = runBlocking {
    val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    val socket = aSocket(ActorSelectorManager(dispatcher)).tcp().connect("127.0.0.1", 2323)

    val client = socket.connection.connectClient()
    try {
        client.doSomething()
    } catch (e: Throwable) {
        dispatcher.close()
        throw e
    }
}
