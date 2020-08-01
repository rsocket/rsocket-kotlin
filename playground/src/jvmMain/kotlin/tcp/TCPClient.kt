package tcp

import doSomething
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.*
import kotlinx.coroutines.*
import java.util.concurrent.*

@OptIn(KtorExperimentalAPI::class)
fun main(): Unit = runBlocking {
    val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    val socket = aSocket(ActorSelectorManager(dispatcher)).tcp().connect("127.0.0.1", 2323)

    val client = socket.rSocketClient()
    try {
        client.doSomething()
    } catch (e: Throwable) {
        dispatcher.close()
        throw e
    }
}
