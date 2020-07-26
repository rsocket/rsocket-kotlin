package dev.whyoleg.rsocket.client

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.connection.*
import dev.whyoleg.rsocket.internal.*
import dev.whyoleg.rsocket.plugin.*

class RSocketClient(
    private val connectionProvider: ConnectionProvider,
    private val configuration: RSocketClientConfiguration = RSocketClientConfiguration()
) {

    suspend fun connect(): RSocket {
        val connection = connectionProvider.connect().let(configuration.plugin::wrapConnection)
        val setupFrame = configuration.setupFrame()
        val connectionSetup = setupFrame.toConnectionSetup()
        val state = RSocketStateImpl(
            connection = connection,
            keepAlive = configuration.keepAlive,
            requestStrategy = configuration.requestStrategy,
            ignoredFrameConsumer = configuration.ignoredFrameConsumer
        )
        val requester = RSocketRequester(state, StreamId.client()).let(configuration.plugin::wrapRequester)
        val acceptor = configuration.acceptor.let(configuration.plugin::wrapAcceptor)
        val requestHandler = acceptor(connectionSetup, requester).let(configuration.plugin::wrapResponder)
        connection.send(setupFrame.toByteArray())
        state.start(requestHandler)
        return requester
    }
}
