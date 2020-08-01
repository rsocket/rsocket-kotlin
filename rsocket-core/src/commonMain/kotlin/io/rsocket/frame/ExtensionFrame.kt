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
import io.rsocket.payload.*

data class ExtensionFrame(
    override val streamId: Int,
    val extendedType: Int,
    val payload: Payload
) : Frame(FrameType.Extension) {
    override val flags: Int get() = if (payload.metadata != null) Flags.Metadata else 0
    override fun Output.writeSelf() {
        writeInt(extendedType)
        writePayload(payload)
    }
}

fun Input.readExtension(streamId: Int, flags: Int): ExtensionFrame {
    val extendedType = readInt()
    val payload = readPayload(flags)
    return ExtensionFrame(streamId, extendedType, payload)
}
