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

import kotlinx.io.*

internal class RequestNFrame(
    override val streamId: Int,
    val requestN: Int,
) : Frame() {
    override val type: FrameType get() = FrameType.RequestN
    override val flags: Int get() = 0

    override fun close(): Unit = Unit

    override fun Sink.writeSelf() {
        writeInt(requestN)
    }

    override fun StringBuilder.appendFlags(): Unit = Unit

    override fun StringBuilder.appendSelf() {
        append("\nRequestN: ").append(requestN)
    }
}

internal fun Source.readRequestN(streamId: Int): RequestNFrame {
    val requestN = readInt()
    return RequestNFrame(streamId, requestN)
}
