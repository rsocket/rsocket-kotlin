package io.rsocket.connection

import io.rsocket.*

interface Connection : Cancelable {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
}
