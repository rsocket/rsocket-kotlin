package dev.whyoleg.rsocket.frame

import io.ktor.utils.io.core.*

data class ResumeOkFrame(
    val lastReceivedClientPosition: Long
) : Frame(FrameType.ResumeOk) {
    override val streamId: Int get() = 0
    override val flags: Int get() = 0

    override fun Output.writeSelf() {
        writeLong(lastReceivedClientPosition)
    }
}

fun Input.readResumeOk(): ResumeOkFrame = ResumeOkFrame(readLong())
