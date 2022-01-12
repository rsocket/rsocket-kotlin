package io.rsocket.kotlin.transport.nodejs.tcp

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public class TcpClientTransport(
    private val port: Int,
    private val hostname: String,
    private val pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : ClientTransport {

    override val coroutineContext: CoroutineContext = coroutineContext + SupervisorJob(coroutineContext[Job])

    @TransportApi
    override suspend fun connect(): Connection {
        val socket = connect(port, hostname)
        return TcpConnection(coroutineContext, pool, socket)
    }
}
