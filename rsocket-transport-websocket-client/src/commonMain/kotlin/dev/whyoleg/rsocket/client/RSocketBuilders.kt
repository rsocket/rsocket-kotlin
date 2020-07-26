package dev.whyoleg.rsocket.client

import dev.whyoleg.rsocket.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

suspend inline fun HttpClient.rSocket(secure: Boolean, request: HttpRequestBuilder.() -> Unit = {}): RSocket = request {
    url {
        protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        port = protocol.defaultPort
    }
    request()
}

suspend inline fun HttpClient.rSocket(
    host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    secure: Boolean = false,
    request: HttpRequestBuilder .() -> Unit = {}
): RSocket = rSocket(secure) {
    url(if (secure) "wss" else "ws", host, port, path)
    request()
}

suspend inline fun HttpClient.rSocket(
    urlString: String,
    secure: Boolean = false,
    request: HttpRequestBuilder .() -> Unit = {}
): RSocket = rSocket(secure) {
    url.takeFrom(urlString)
    request()
}
