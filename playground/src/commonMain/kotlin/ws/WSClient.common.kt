package ws

import doSomething
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.websocket.*
import io.rsocket.client.*

expect val engine: HttpClientEngineFactory<*>

suspend fun run() {
    val client = HttpClient(engine) {
        install(WebSockets)
        install(RSocketClientSupport)
    }

    val rSocket = client.rSocket()
    rSocket.doSomething()
}
