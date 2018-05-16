package io.rsocket.android.test

import io.rsocket.android.transport.netty.client.TcpClientTransport
import io.rsocket.android.transport.netty.server.TcpServerTransport

class NettyTcpEndToEndTest
    : EndToEndTest(
        { TcpClientTransport.create(it) },
        { TcpServerTransport.create(it) }) {

    override fun response() {
    }
}