package io.rsocket.transport.okhttp.client

import io.rsocket.transport.okhttp.OkWebSocket
import io.reactivex.Single
import io.rsocket.android.DuplexConnection
import io.rsocket.android.transport.ClientTransport
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Created by Maksym Ostroverkhov on 28.10.17.
 */
class OkhttpWebsocketClientTransport private constructor(private val client: OkHttpClient,
                                                         private val request: Request) : ClientTransport {
    override fun connect(): Single<DuplexConnection> = Single.defer {
        OkWebSocket(client, request).connected()
    }

    companion object {
        fun create(request: Request): OkhttpWebsocketClientTransport = create(defaultClient, request)

        fun create(url: HttpUrl): OkhttpWebsocketClientTransport = create(defaultClient, request(url))

        fun create(client: OkHttpClient, url: HttpUrl): OkhttpWebsocketClientTransport = create(client, request(url))

        fun create(client: OkHttpClient, request: Request): OkhttpWebsocketClientTransport =
                OkhttpWebsocketClientTransport(client, request)

        private val defaultClient by lazy { OkHttpClient() }

        private fun request(url: HttpUrl) = Request.Builder().url(url).build()
    }
}