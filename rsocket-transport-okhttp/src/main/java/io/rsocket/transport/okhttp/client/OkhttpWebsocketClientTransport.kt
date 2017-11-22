package io.rsocket.transport.okhttp.client

import io.rsocket.transport.okhttp.OkWebsocket
import io.reactivex.Single
import io.rsocket.android.DuplexConnection
import io.rsocket.android.transport.ClientTransport

/**
 * Created by Maksym Ostroverkhov on 28.10.17.
 */
class OkhttpWebsocketClientTransport private constructor(private val scheme: String,
                                                         private val host: String,
                                                         private val port: Int) : ClientTransport {
    override fun connect(): Single<DuplexConnection> = Single.defer {
        OkWebsocket(scheme, host, port).connected()
    }

    companion object {
        fun create(scheme: String, host: String, port: Int): OkhttpWebsocketClientTransport =
                OkhttpWebsocketClientTransport(scheme, host, port)
    }
}