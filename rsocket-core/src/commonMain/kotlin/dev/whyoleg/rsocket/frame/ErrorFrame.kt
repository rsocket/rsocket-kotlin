package dev.whyoleg.rsocket.frame

import dev.whyoleg.rsocket.error.*
import io.ktor.utils.io.core.*

data class ErrorFrame(
    override val streamId: Int,
    val throwable: Throwable,
    val data: ByteArray? = null
) : Frame(FrameType.Error) {
    override val flags: Int get() = 0
    val errorCode get() = (throwable as? RSocketError)?.errorCode ?: ErrorCode.ApplicationError

    override fun Output.writeSelf() {
        writeInt(errorCode)
        when (data) {
            null -> writeText(throwable.message ?: "")
            else -> writeFully(data)
        }
    }
}

fun Input.readError(streamId: Int): ErrorFrame {
    val errorCode = readInt()
    val message = readText()
    return ErrorFrame(streamId, RSocketError(streamId, errorCode, message))
}
