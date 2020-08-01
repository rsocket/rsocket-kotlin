package io.rsocket.frame

import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*

data class LeaseFrame(
    val ttl: Int,
    val numberOfRequests: Int,
    val metadata: ByteArray?
) : Frame(FrameType.Lease) {
    override val streamId: Int get() = 0
    override val flags: Int get() = if (metadata != null) Flags.Metadata else 0
    override fun Output.writeSelf() {
        writeInt(ttl)
        writeInt(numberOfRequests)
        writeMetadata(metadata)
    }
}

fun Input.readLease(flags: Int): LeaseFrame {
    val ttl = readInt()
    val numberOfRequests = readInt()
    val metadata = if (flags check Flags.Metadata) readMetadata() else null
    return LeaseFrame(ttl, numberOfRequests, metadata)
}
