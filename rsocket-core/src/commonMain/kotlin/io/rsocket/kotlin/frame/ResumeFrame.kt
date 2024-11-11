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

import io.rsocket.kotlin.frame.io.*
import kotlinx.io.*

internal class ResumeFrame(
    val version: Version,
    val resumeToken: Buffer,
    val lastReceivedServerPosition: Long,
    val firstAvailableClientPosition: Long,
) : Frame() {
    override val type: FrameType get() = FrameType.Resume
    override val streamId: Int get() = 0
    override val flags: Int get() = 0

    override fun close(): Unit = Unit

    override fun Sink.writeSelf() {
        writeVersion(version)
        writeResumeToken(resumeToken)
        writeLong(lastReceivedServerPosition)
        writeLong(firstAvailableClientPosition)
    }

    override fun StringBuilder.appendFlags(): Unit = Unit

    override fun StringBuilder.appendSelf() {
        append("\nVersion: ").append(version.toString()).append("\n")
        append("Last received server position: ").append(lastReceivedServerPosition).append("\n")
        append("First available client position: ").append(firstAvailableClientPosition)
        appendBuffer("Resume token", resumeToken)
    }
}

internal fun Source.readResume(): ResumeFrame {
    val version = readVersion()
    val resumeToken = readResumeToken()
    val lastReceivedServerPosition = readLong()
    val firstAvailableClientPosition = readLong()
    return ResumeFrame(
        version = version,
        resumeToken = resumeToken,
        lastReceivedServerPosition = lastReceivedServerPosition,
        firstAvailableClientPosition = firstAvailableClientPosition
    )
}
