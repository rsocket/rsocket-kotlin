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

import io.rsocket.kotlin.internal.io.*
import kotlinx.io.*
import kotlin.test.*

internal fun Frame.toBufferWithLength(): Buffer = Buffer().apply {
    val packet = Buffer()
    writeInt24(packet.transferFrom(toBuffer()).toInt())
    transferFrom(packet)
}

internal fun Buffer.toFrameWithLength(): Frame {
    val length = readInt24()
    assertEquals(length, size.toInt())
    return readFrame()
}

internal fun Frame.loopFrame(): Frame = toBuffer().readFrame()
