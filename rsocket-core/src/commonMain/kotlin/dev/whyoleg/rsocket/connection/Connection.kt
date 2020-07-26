package dev.whyoleg.rsocket.connection

import dev.whyoleg.rsocket.*

interface Connection : Cancelable {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
}
