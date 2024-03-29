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

package io.rsocket.kotlin.frame

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

internal fun Frame.toPacketWithLength(): ByteReadPacket = InUseTrackingPool.buildPacket {
    val packet = toPacket(InUseTrackingPool)
    writeInt24(packet.remaining.toInt())
    writePacket(packet)
}

internal fun ByteReadPacket.toFrameWithLength(): Frame {
    val length = readInt24()
    assertEquals(length, remaining.toInt())
    return readFrame(InUseTrackingPool)
}

internal fun Frame.loopFrame(): Frame = toPacket(InUseTrackingPool).readFrame(InUseTrackingPool)
