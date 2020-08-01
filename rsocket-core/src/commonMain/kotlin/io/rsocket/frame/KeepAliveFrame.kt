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

package io.rsocket.frame

import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*

private const val KeepAliveFlag = 128

class KeepAliveFrame(
    val respond: Boolean,
    val lastPosition: Long,
    val data: ByteReadPacket
) : Frame(FrameType.KeepAlive) {
    override val streamId: Int get() = 0
    override val flags: Int get() = if (respond) KeepAliveFlag else 0

    override fun Output.writeSelf() {
        writeLong(lastPosition.coerceAtLeast(0))
        writePacket(data)
    }
}

fun Input.readKeepAlive(flags: Int): KeepAliveFrame {
    val respond = flags check KeepAliveFlag
    val lastPosition = readLong()
    val data = readPacket()
    return KeepAliveFrame(respond, lastPosition, data)
}
