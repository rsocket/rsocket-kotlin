package io.rsocket.kotlin.transport.nodejs.tcp

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.js.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import kotlin.coroutines.*

@TransportApi
internal class TcpConnection(
    override val coroutineContext: CoroutineContext,
    override val pool: ObjectPool<ChunkBuffer>,
    private val socket: Socket
) : Connection {

    private val sendChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(8)
    private val receiveChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(Channel.UNLIMITED)

    init {
        launch {
            sendChannel.consumeEach { packet ->
                socket.write(Uint8Array(packet.withLength().readArrayBuffer()))
            }
        }

        coroutineContext.job.invokeOnCompletion {
            when (it) {
                null -> socket.destroy()
                else -> socket.destroy(Error(it.message, it.cause))
            }
        }

        val frameAssembler = FrameWithLengthAssembler { receiveChannel.trySend(it) } //TODO
        socket.on(
            onData = { frameAssembler.write { writeFully(it.buffer) } },
            onError = { coroutineContext.job.cancel("Socket error", it) },
            onClose = { if (!it) coroutineContext.job.cancel("Socket closed") }
        )
    }

    override suspend fun send(packet: ByteReadPacket) {
        sendChannel.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        return receiveChannel.receive()
    }
}
