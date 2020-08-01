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
import io.rsocket.keepalive.*
import io.rsocket.payload.*

private const val HonorLeaseFlag = 64
private const val ResumeEnabledFlag = 128

class SetupFrame(
    val version: Version, //TODO check
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val resumeToken: ByteReadPacket?,
    val payloadMimeType: PayloadMimeType,
    val payload: Payload
) : Frame(FrameType.Setup) {
    override val streamId: Int get() = 0
    override val flags: Int
        get() {
            var flags = 0
            if (honorLease) flags = flags or HonorLeaseFlag
            if (resumeToken != null) flags = flags or ResumeEnabledFlag
            if (payload.metadata != null) flags = flags or Flags.Metadata
            return flags
        }

    override fun Output.writeSelf() {
        writeVersion(version)
        writeKeepAlive(keepAlive)
        writeResumeToken(resumeToken)
        writePayloadMimeType(payloadMimeType)
        writePayload(payload)
    }
}

fun Input.readSetup(flags: Int): SetupFrame {
    val version = readVersion()
    val keepAlive = readKeepAlive()
    val resumeToken = if (flags check ResumeEnabledFlag) readResumeToken() else null
    val payloadMimeType = readPayloadMimeType()
    val payload = readPayload(flags)
    return SetupFrame(
        version = version,
        honorLease = flags check HonorLeaseFlag,
        keepAlive = keepAlive,
        resumeToken = resumeToken,
        payloadMimeType = payloadMimeType,
        payload = payload
    )
}
