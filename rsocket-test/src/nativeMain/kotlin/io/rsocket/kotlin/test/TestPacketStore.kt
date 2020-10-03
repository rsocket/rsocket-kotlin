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

package io.rsocket.kotlin.test

import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*

actual class TestPacketStore {
    private val sentIndex = atomic(0)
    private val _stored = atomicArrayOfNulls<ByteReadPacket>(100) //max 100 in cache

    actual val stored: List<ByteReadPacket>
        get() = buildList {
            repeat(sentIndex.value) {
                add(_stored[it].value!!)
            }
        }

    actual fun store(packet: ByteReadPacket) {
        _stored[sentIndex.getAndIncrement()].value = packet
    }
}
