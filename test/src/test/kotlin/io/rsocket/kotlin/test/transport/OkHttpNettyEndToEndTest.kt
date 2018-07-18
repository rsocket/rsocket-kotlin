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

package io.rsocket.kotlin.test.transport

import io.rsocket.kotlin.transport.netty.server.WebsocketServerTransport
import io.rsocket.transport.okhttp.client.OkhttpWebsocketClientTransport
import okhttp3.HttpUrl

class OkHttpNettyEndToEndTest : EndToEndTest(
        {
            OkhttpWebsocketClientTransport.create(
                    HttpUrl.Builder()
                            .host(it.hostName)
                            .port(it.port)
                            .scheme("http")
                            .build())
        },
        { WebsocketServerTransport.create(it.hostName, it.port) })
