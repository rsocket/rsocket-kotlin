package dev.whyoleg.rsocket.frame

import io.ktor.utils.io.core.*

data class CancelFrame(
    override val streamId: Int
) : Frame(FrameType.Cancel) {
    override val flags: Int get() = 0
    override fun Output.writeSelf(): Unit = Unit
}
