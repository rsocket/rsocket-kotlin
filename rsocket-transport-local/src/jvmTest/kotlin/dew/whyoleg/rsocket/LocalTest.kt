package dew.whyoleg.rsocket

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.client.*
import dev.whyoleg.rsocket.connection.*
import dev.whyoleg.rsocket.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalTest : TransportTest() {
    override suspend fun init(): RSocket {
        val clientChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverConnection = LocalConnection("server", clientChannel, serverChannel)
        val clientConnection = LocalConnection("client", serverChannel, clientChannel)
        return coroutineScope {
            launch {
                RSocketServer(ConnectionProvider(serverConnection)).start { TestRSocket() }
            }
            RSocketClient(ConnectionProvider(clientConnection)).connect()
        }
    }
}
