package io.rsocket.android.test

import io.rsocket.android.transport.netty.server.WebsocketServerTransport
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
