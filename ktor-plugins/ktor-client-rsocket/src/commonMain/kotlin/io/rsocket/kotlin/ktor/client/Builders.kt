/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.ktor.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.kotlin.*

public suspend fun HttpClient.rSocket(
    request: HttpRequestBuilder.() -> Unit,
): RSocket = connectRSocket(request)

public suspend fun HttpClient.rSocket(
    urlString: String,
    secure: Boolean = false,
    request: HttpRequestBuilder.() -> Unit = {},
): RSocket = rSocket {
    url {
        this.protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        this.port = protocol.defaultPort
        takeFrom(urlString)
    }
    request()
}

public suspend fun HttpClient.rSocket(
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    secure: Boolean = false,
    request: HttpRequestBuilder.() -> Unit = {},
): RSocket = rSocket {
    url {
        this.protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        this.port = protocol.defaultPort
        set(host = host, port = port, path = path)
    }
    request()
}
