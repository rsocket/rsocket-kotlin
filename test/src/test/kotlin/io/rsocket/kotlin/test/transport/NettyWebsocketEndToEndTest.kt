package io.rsocket.kotlin.test.transport

import io.rsocket.kotlin.transport.netty.client.WebsocketClientTransport
import io.rsocket.kotlin.transport.netty.server.WebsocketServerTransport

class NettyWebsocketEndToEndTest : EndToEndTest(
        { WebsocketClientTransport.create(it) },
        { WebsocketServerTransport.create(it.port) })
