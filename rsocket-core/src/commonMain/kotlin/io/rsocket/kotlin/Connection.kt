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

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.frame.*

/**
 * That interface isn't stable for inheritance.
 */
@TransportApi
interface Connection : Cancelable {

    @DangerousInternalIoApi
    val pool: ObjectPool<ChunkBuffer>
        get() = ChunkBuffer.Pool

    suspend fun send(packet: ByteReadPacket)
    suspend fun receive(): ByteReadPacket
}

@OptIn(DangerousInternalIoApi::class, TransportApi::class)
internal suspend fun Connection.receiveFrame(): Frame = receive().readFrame(pool)

@OptIn(DangerousInternalIoApi::class, TransportApi::class)
internal suspend fun Connection.sendFrame(frame: Frame): Unit = send(frame.toPacket(pool))
