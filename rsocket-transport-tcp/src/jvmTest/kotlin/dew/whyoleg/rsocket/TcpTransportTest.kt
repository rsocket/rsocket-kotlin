package dew.whyoleg.rsocket

import dev.whyoleg.rsocket.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.*

@OptIn(KtorExperimentalAPI::class)
class TcpTransportTest : TransportTest() {
    override suspend fun init(): RSocket = builder.connect("127.0.0.1", 2323).rSocketClient()

    companion object {
        private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
        private val builder = aSocket(ActorSelectorManager(dispatcher)).tcp()

        init {
            GlobalScope.launch {
                builder.bind("127.0.0.1", 2323).rSocket {
                    TestRSocket()
                }
            }
        }
    }
}
