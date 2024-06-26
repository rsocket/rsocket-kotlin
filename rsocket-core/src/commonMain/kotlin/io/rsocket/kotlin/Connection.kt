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

package io.rsocket.kotlin

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import kotlinx.coroutines.*

/**
 * That interface isn't stable for inheritance.
 */
@TransportApi
public interface Connection : CoroutineScope {
    public suspend fun send(packet: ByteReadPacket)
    public suspend fun receive(): ByteReadPacket
}

@OptIn(TransportApi::class)
internal suspend inline fun <T> Connection.receiveFrame(pool: BufferPool, block: (frame: Frame) -> T): T =
    receive().readFrame(pool).closeOnError(block)

@OptIn(TransportApi::class)
internal suspend fun Connection.sendFrame(pool: BufferPool, frame: Frame) {
    frame.toPacket(pool).closeOnError { send(it) }
}
