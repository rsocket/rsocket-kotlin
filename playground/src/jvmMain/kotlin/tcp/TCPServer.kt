package tcp

import dev.whyoleg.rsocket.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import rSocketAcceptor
import java.util.concurrent.*

@OptIn(KtorExperimentalAPI::class)
fun main(): Unit = runBlocking {
    val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    val server = aSocket(ActorSelectorManager(dispatcher)).tcp().bind("127.0.0.1", 2323)

    server.rSocket(acceptor = rSocketAcceptor)
}
