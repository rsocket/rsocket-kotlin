package dev.whyoleg.rsocket.frame

import dev.whyoleg.rsocket.frame.io.*
import io.ktor.utils.io.core.*

data class MetadataPushFrame(
    val metadata: ByteArray
) : Frame(FrameType.MetadataPush) {
    override val streamId: Int get() = 0
    override val flags: Int get() = Flags.Metadata

    override fun Output.writeSelf() {
        writeFully(metadata)
    }
}

fun Input.readMetadataPush(): MetadataPushFrame = MetadataPushFrame(readBytes())
