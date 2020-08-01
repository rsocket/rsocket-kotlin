package io.rsocket.frame

import io.ktor.utils.io.core.*

data class RequestNFrame(
    override val streamId: Int,
    val requestN: Int
) : Frame(FrameType.RequestN) {
    override val flags: Int get() = 0
    override fun Output.writeSelf() {
        writeInt(requestN)
    }
}

fun Input.readRequestN(streamId: Int): RequestNFrame {
    val requestN = readInt()
    return RequestNFrame(streamId, requestN)
}
