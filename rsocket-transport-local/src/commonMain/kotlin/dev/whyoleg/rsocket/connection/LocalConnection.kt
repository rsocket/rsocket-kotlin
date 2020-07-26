package dev.whyoleg.rsocket.connection

import dev.whyoleg.rsocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalConnection(
    private val name: String,
    private val sender: Channel<ByteArray>,
    private val receiver: Channel<ByteArray>,
    parentJob: Job? = null
) : Connection, Cancelable {
    override val job: Job = Job(parentJob)

    override suspend fun send(bytes: ByteArray) {
        sender.send(bytes)
    }

    override suspend fun receive(): ByteArray {
        return receiver.receive()
    }
}
