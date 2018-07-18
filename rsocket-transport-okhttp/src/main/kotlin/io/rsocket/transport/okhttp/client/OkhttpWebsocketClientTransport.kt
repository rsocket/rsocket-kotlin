/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.transport.okhttp.client

import io.rsocket.transport.okhttp.OkWebsocket
import io.reactivex.Single
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.transport.ClientTransport
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OkhttpWebsocketClientTransport private constructor(private val client: OkHttpClient,
                                                         private val request: Request)
    : ClientTransport {
    override fun connect(): Single<DuplexConnection> =
            Single.defer { OkWebsocket(client, request).onConnect() }

    companion object {
        fun create(request: Request): OkhttpWebsocketClientTransport =
                create(defaultClient, request)

        fun create(url: HttpUrl): OkhttpWebsocketClientTransport =
                create(defaultClient, request(url))

        fun create(client: OkHttpClient,
                   url: HttpUrl): OkhttpWebsocketClientTransport =
                create(client, request(url))

        fun create(client: OkHttpClient, request: Request)
                : OkhttpWebsocketClientTransport =
                OkhttpWebsocketClientTransport(client, request)

        private val defaultClient by lazy { OkHttpClient() }

        private fun request(url: HttpUrl) = Request.Builder().url(url).build()
    }
}