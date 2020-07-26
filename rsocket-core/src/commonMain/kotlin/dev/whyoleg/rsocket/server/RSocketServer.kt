package dev.whyoleg.rsocket.server

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.connection.*
import dev.whyoleg.rsocket.error.*
import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.frame.io.*
import dev.whyoleg.rsocket.internal.*
import dev.whyoleg.rsocket.plugin.*
import kotlinx.coroutines.*

class RSocketServer(
    private val connectionProvider: ConnectionProvider,
    private val configuration: RSocketServerConfiguration = RSocketServerConfiguration()
) {
    suspend fun start(acceptor: RSocketAcceptor): Job {
        val connection = connectionProvider.connect().let(configuration.plugin::wrapConnection)
        return when (val setupFrame = connection.receive().toFrame()) {
            is SetupFrame -> {
                if (setupFrame.version != Version.Current) {
                    connection.failSetup(RSocketError.Setup.Invalid("Unsupported version: ${setupFrame.version}"))
                }
                //check lease, resume
                val connectionSetup = setupFrame.toConnectionSetup()
                val state = RSocketStateImpl(
                    connection = connection,
                    keepAlive = connectionSetup.keepAlive,
                    requestStrategy = configuration.requestStrategy,
                    ignoredFrameConsumer = configuration.ignoredFrameConsumer
                )
                val requester = RSocketRequester(state, StreamId.server()).let(configuration.plugin::wrapRequester)
                val requestHandler = try {
                    val wrappedAcceptor = configuration.plugin.wrapAcceptor(acceptor)
                    wrappedAcceptor(connectionSetup, requester).let(configuration.plugin::wrapResponder)
                } catch (e: Throwable) {
                    connection.failSetup(RSocketError.Setup.Rejected(e.message ?: "Rejected by server acceptor"))
                }
                state.start(requestHandler)
            }
            else          -> connection.failSetup(RSocketError.Setup.Invalid("Invalid setup frame: ${setupFrame.type}"))
        }
    }

    private suspend fun Connection.failSetup(error: RSocketError.Setup): Nothing {
        send(ErrorFrame(0, error).toByteArray())
        cancel("Setup failed", error)
        throw error
    }
}
