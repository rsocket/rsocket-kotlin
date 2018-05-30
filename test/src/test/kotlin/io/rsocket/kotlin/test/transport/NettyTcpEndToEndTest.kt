package io.rsocket.kotlin.test.transport

import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport

class NettyTcpEndToEndTest
    : EndToEndTest(
        { TcpClientTransport.create(it) },
        { TcpServerTransport.create(it) }) {

    override fun response() {
    }
}