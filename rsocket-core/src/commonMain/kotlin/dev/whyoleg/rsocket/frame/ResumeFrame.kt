package dev.whyoleg.rsocket.frame

import dev.whyoleg.rsocket.frame.io.*
import io.ktor.utils.io.core.*

data class ResumeFrame(
    val version: Version,
    val resumeToken: ByteArray,
    val lastReceivedServerPosition: Long,
    val firstAvailableClientPosition: Long
) : Frame(FrameType.Resume) {
    override val streamId: Int get() = 0
    override val flags: Int get() = 0
    override fun Output.writeSelf() {
        writeVersion(version)
        writeResumeToken(resumeToken)
        writeLong(lastReceivedServerPosition)
        writeLong(firstAvailableClientPosition)
    }
}

fun Input.readResume(): ResumeFrame {
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
