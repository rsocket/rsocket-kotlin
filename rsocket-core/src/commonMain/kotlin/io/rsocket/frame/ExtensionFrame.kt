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
