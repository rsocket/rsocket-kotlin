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

package io.rsocket.connection

import io.rsocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalConnection(
    private val name: String,
    private val sender: Channel<ByteArray>,
    private val receiver: Channel<ByteArray>,
    parentJob: Job? = null
) : Connection, Cancelable {
    override val job: Job = Job(parentJob)

    override suspend fun send(bytes: ByteArray) {
        sender.send(bytes)
    }

    override suspend fun receive(): ByteArray {
        return receiver.receive()
    }
}
