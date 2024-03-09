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

package io.rsocket.kotlin.transport.local

import io.rsocket.kotlin.transport.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.random.*

internal class LocalServerInstanceImpl @RSocketTransportApi constructor(
    override val serverName: String,
    override val coroutineContext: CoroutineContext,
    private val acceptor: RSocketServerAcceptor,
    private val connector: LocalServerConnector,
) : LocalServerInstance {

    init {
        register(serverName, this)
        coroutineContext.job.invokeOnCompletion { unregister(serverName) }
    }

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession = connect(this)

    @RSocketTransportApi
    fun connect(clientScope: CoroutineScope): RSocketTransportSession = connector.connect(acceptor, clientScope, this)

    companion object {
        private val lock = SynchronizedObject()
        private val instances = mutableMapOf<String, LocalServerInstanceImpl>()

        private fun register(name: String, target: LocalServerInstanceImpl): Unit = synchronized(lock) {
            check(name !in instances) { "Already registered: $name" }
            instances[name] = target
        }

        private fun unregister(name: String): Unit = synchronized(lock) {
            instances.remove(name)
        }

        fun get(name: String): LocalServerInstanceImpl = synchronized(lock) {
            checkNotNull(instances[name]) { "Cannot find $name" }
        }

        @OptIn(ExperimentalStdlibApi::class)
        fun randomName(): String = Random.nextBytes(16).toHexString(HexFormat.UpperCase)
    }
}
