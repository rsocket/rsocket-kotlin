package dev.whyoleg.rsocket.frame.io

import io.ktor.utils.io.core.*

private const val lengthMask: Int = 0xFFFFFF.inv()

fun Input.readLength(): Int {
    val b = readByte().toInt() and 0xFF shl 16
    val b1 = readByte().toInt() and 0xFF shl 8
    val b2 = readByte().toInt() and 0xFF
    return b or b1 or b2
}

fun Output.writeLength(length: Int) {
    require(length and lengthMask == 0) { "Length is larger than 24 bits" }
    writeByte((length shr 16).toByte())
    writeByte((length shr 8).toByte())
    writeByte(length.toByte())
}
