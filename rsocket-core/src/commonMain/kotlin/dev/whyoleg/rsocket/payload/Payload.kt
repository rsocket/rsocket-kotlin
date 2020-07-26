package dev.whyoleg.rsocket.payload

//TODO remove data
data class Payload(
    val metadata: ByteArray?,
    val data: ByteArray
) {
    companion object {
        val Empty = Payload(null, byteArrayOf())
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun Payload(metadata: String?, data: String): Payload = Payload(metadata?.encodeToByteArray(), data.encodeToByteArray())
