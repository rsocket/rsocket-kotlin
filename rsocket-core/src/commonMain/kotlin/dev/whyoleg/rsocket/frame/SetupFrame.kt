package dev.whyoleg.rsocket.frame

import dev.whyoleg.rsocket.frame.io.*
import dev.whyoleg.rsocket.keepalive.*
import dev.whyoleg.rsocket.payload.*
import io.ktor.utils.io.core.*

private const val HonorLeaseFlag = 64
private const val ResumeEnabledFlag = 128

data class SetupFrame constructor(
    val version: Version, //TODO check
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val resumeToken: ByteArray?,
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
