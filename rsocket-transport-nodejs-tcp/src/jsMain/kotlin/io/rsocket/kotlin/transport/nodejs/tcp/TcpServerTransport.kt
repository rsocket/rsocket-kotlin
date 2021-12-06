package io.rsocket.kotlin.transport.nodejs.tcp

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public class TcpServer internal constructor(
    public val job: Job, private val server: Server
) {
    public suspend fun close(): Unit = suspendCancellableCoroutine { cont ->
        server.close { cont.resume(Unit) }
    }
}

public class TcpServerTransport(
    private val port: Int, private val hostname: String, private val pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : ServerTransport<TcpServer> {
    @TransportApi
    override fun CoroutineScope.start(accept: suspend CoroutineScope.(Connection) -> Unit): TcpServer {
        val supervisorJob = SupervisorJob(coroutineContext[Job])
        val server = createServer(port, hostname, { supervisorJob.cancel() }) {
            launch(supervisorJob) {
                accept(TcpConnection(coroutineContext, pool, it))
            }
        }
        return TcpServer(supervisorJob, server)
    }
}
