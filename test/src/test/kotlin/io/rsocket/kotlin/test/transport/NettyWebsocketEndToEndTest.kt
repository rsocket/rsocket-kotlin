package io.rsocket.kotlin.test.transport

import io.rsocket.kotlin.test.transport.EndToEndTest
import io.rsocket.kotlin.transport.netty.client.WebsocketClientTransport
import io.rsocket.kotlin.transport.netty.server.WebsocketServerTransport

class NettyWebsocketEndToEndTest : EndToEndTest(
        { WebsocketClientTransport.create(it) },
        { WebsocketServerTransport.create(it.port) }) {

    /*noop until https://github.com/reactor/reactor-netty/pull/346 is released*/
    override fun close() {
    }

    override fun closedAvailability() {
    }
}