package dev.whyoleg.rsocket.frame.io

import dev.whyoleg.rsocket.payload.*
import io.ktor.utils.io.core.*

fun Input.readMetadata(): ByteArray {
    val length = readLength()
    return readBytes(length)
}

fun Output.writeMetadata(metadata: ByteArray?) {
    metadata?.let {
        writeLength(it.size)
        writeFully(it)
    }
}

fun Input.readPayload(flags: Int): Payload {
    val metadata = if (flags check Flags.Metadata) readMetadata() else null
    val data = readBytes()
    return Payload(metadata, data)
}

fun Output.writePayload(payload: Payload) {
    writeMetadata(payload.metadata)
    writeFully(payload.data)
}
