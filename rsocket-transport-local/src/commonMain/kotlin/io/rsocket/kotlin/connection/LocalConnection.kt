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

package io.rsocket.kotlin.connection

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalConnection(
    private val name: String,
    private val sender: Channel<ByteReadPacket>,
    private val receiver: Channel<ByteReadPacket>,
    parentJob: Job? = null
) : Connection, Cancelable {
    override val job: Job = Job(parentJob)

    override suspend fun send(packet: ByteReadPacket) {
//        println("SEND: ${packet.copy().readBytes().contentToString()}")
        sender.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        return receiver.receive().also {
//            println("RECEIVE: ${it.copy().readBytes().contentToString()}")
        }
    }
}
