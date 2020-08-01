package io.rsocket.connection

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*
import kotlinx.coroutines.*

//TODO need to check and extract length support!!
@OptIn(KtorExperimentalAPI::class)
class KtorTcpConnection(private val socket: Socket) : Connection {
    override val job: Job get() = socket.socketContext

    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(true)

    override suspend fun send(bytes: ByteArray) {
        val length = bytes.size
        writeChannel.writePacket {
            writeLength(length)
            writeFully(bytes)
        }
    }

    override suspend fun receive(): ByteArray = readChannel.run {
        val length = readPacket(3).readLength()
        val bytes = readPacket(length).readBytes()
        bytes
    }
}
