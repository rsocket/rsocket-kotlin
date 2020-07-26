package dev.whyoleg.rsocket.payload

data class PayloadMimeType(
    val metadata: String = "application/binary",
    val data: String = "application/binary"
)
