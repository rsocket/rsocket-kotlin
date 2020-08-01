package io.rsocket.frame.io

import io.ktor.utils.io.core.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*
import kotlin.time.*

fun Input.readResumeToken(): ByteArray {
    val length = readShort().toInt() and 0xFFFF
    return readBytes(length)
}

fun Output.writeResumeToken(resumeToken: ByteArray?) {
    resumeToken?.let {
        writeShort(it.size.toShort())
        writeFully(it)
    }
}

fun Input.readMimeType(): String {
    val length = readByte().toInt()
    return readText(max = length)
}

@OptIn(ExperimentalStdlibApi::class)
fun Output.writeMimeType(mimeType: String) {
    val bytes = mimeType.encodeToByteArray() //TODO check
    writeByte(bytes.size.toByte())
    writeFully(bytes)
}

fun Input.readPayloadMimeType(): PayloadMimeType {
    val metadata = readMimeType()
    val data = readMimeType()
    return PayloadMimeType(metadata, data)
}

fun Output.writePayloadMimeType(payloadMimeType: PayloadMimeType) {
    writeMimeType(payloadMimeType.metadata)
    writeMimeType(payloadMimeType.data)
}

fun Input.readMillis(): Duration = readInt().milliseconds

fun Output.writeMillis(duration: Duration) {
    writeInt(duration.toInt(DurationUnit.MILLISECONDS))
}

fun Input.readKeepAlive(): KeepAlive {
    val interval = readMillis()
    val maxLifetime = readMillis()
    return KeepAlive(interval, maxLifetime)
}

fun Output.writeKeepAlive(keepAlive: KeepAlive) {
    writeMillis(keepAlive.interval)
    writeMillis(keepAlive.maxLifetime)
}
