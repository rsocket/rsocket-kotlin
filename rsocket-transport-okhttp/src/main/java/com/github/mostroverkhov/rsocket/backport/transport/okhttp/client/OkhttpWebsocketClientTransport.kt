package com.github.mostroverkhov.rsocket.backport.transport.okhttp.client

import com.github.mostroverkhov.rsocket.backport.transport.okhttp.OkWebsocket
import io.reactivex.Single
import io.rsocket.DuplexConnection
import io.rsocket.transport.ClientTransport

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