package io.rsocket.android.test

import io.rsocket.android.transport.netty.client.WebsocketClientTransport
import io.rsocket.android.transport.netty.server.WebsocketServerTransport

class NettyWebsocketEndToEndTest : EndToEndTest(
        { WebsocketClientTransport.create(it) },
        { WebsocketServerTransport.create(it.port) }) {

    /*noop until https://github.com/reactor/reactor-netty/pull/346 is released*/
    override fun close() {
    }

    override fun closedAvailability() {
    }
}