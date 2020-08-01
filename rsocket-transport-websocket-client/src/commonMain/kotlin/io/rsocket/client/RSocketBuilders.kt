/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.*

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
