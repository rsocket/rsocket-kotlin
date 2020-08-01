package io.rsocket.frame

import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*

private const val KeepAliveFlag = 128

data class KeepAliveFrame(
    val respond: Boolean,
    val lastPosition: Long,
    val data: ByteArray
) : Frame(FrameType.KeepAlive) {
    override val streamId: Int get() = 0
    override val flags: Int get() = if (respond) KeepAliveFlag else 0

    override fun Output.writeSelf() {
        writeLong(lastPosition.coerceAtLeast(0))
        writeFully(data)
    }
}

fun Input.readKeepAlive(flags: Int): KeepAliveFrame {
    val respond = flags check KeepAliveFlag
    val lastPosition = readLong()
    val data = readBytes()
    return KeepAliveFrame(respond, lastPosition, data)
}
