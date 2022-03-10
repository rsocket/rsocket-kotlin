@file:OptIn(TransportApi::class)

package io.rsocket.kotlin.connect

import io.rsocket.kotlin.*
import io.rsocket.kotlin.configuration.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.time.*

public sealed interface RSocketContext {
    public val session: RSocketSession
    public val configuration: RSocketConfiguration
}

public sealed interface ConnectedRSocket : RSocket, RSocketContext

//TODO decide, do we need interceptors somewhere?
@ConnectConfigurationDsl
public sealed interface RSocketConnectContext : RSocketContext {
    override val configuration: RSocketConnectConfiguration

    //requests will be delayed to connect completed
    //calling requests during configuration is prohibited???
    public val requester: RSocket

    @ConnectConfigurationDsl
    public fun responder(rsocket: RSocket)
}

@ConnectConfigurationDsl
public inline fun RSocketConnectContext.responder(block: RSocketBuilder.() -> Unit) {
    responder(RSocket(block))
}

@ConnectConfigurationDsl
public inline operator fun <C : ConnectConfiguration> C.invoke(block: C.() -> Unit) {
    apply(block)
}

public sealed interface RSocketClientConnectContext : RSocketConnectContext {
    override val configuration: RSocketClientConnectConfiguration
}

public sealed interface RSocketServerConnectContext : RSocketConnectContext {
    override val configuration: RSocketServerConnectConfiguration
}

internal class ConnectedRSocketImpl(
    override val session: RSocketSession,
    override val configuration: RSocketConfiguration,
    rsocket: RSocket,
) : ConnectedRSocket, RSocket by rsocket

internal abstract class RSocketConnectContextImpl(
    final override val session: RSocketSession,
    deferredRequester: Deferred<RSocket>,
) : RSocketConnectContext, ConfigurationState {
    protected abstract val isServer: Boolean
    abstract override val configuration: RSocketConnectConfigurationImpl
    private var configured by atomic(false)
    private var responder: RSocket? = null
    final override var requester: RSocket = DeferredRequester(this, deferredRequester)
        private set

    init {
        session.coroutineContext.job.invokeOnCompletion {
            configuration.close()
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        deferredRequester.invokeOnCompletion {
            if (deferredRequester.getCompletionExceptionOrNull() == null) {
                requester = deferredRequester.getCompleted()
            }
        }
    }

    override fun responder(rsocket: RSocket) {
        checkNotConfigured()
        check(responder == null) { "Responder can be set only once" }
        responder = rsocket
    }

    override fun checkNotConfigured() {
        check(!configured) { "Configuration phase is finished, no more changing is possible" }
    }

    override fun checkConfigured() {
        check(configured) { "Configuration phase is not finished yet" }
    }

    private fun completeConfiguration() {
        configured = true
    }

    suspend fun configure(
        beforeConfigurators: List<RSocketConnectConfigurator>,
        afterConfigurators: List<RSocketConnectConfigurator>,
        peerConfigurator: RSocketConnectConfigurator,
    ) {
        try {
            beforeConfigurators.forEach {
                with(it) { configure() }
            }
            with(peerConfigurator) { configure() }
            afterConfigurators.forEach {
                with(it) { configure() }
            }
            completeConfiguration()
        } catch (cause: Throwable) {
            when (cause) {
                is CancellationException -> session.cancel(cause)
                else                     -> session.cancel("Configuration failed", cause)
            }
            throw cause
        }
    }

    protected abstract suspend fun onConnect(connection: Connection)

    suspend fun connect(connection: Connection): RSocket {
        val prioritizer = Prioritizer()
        val frameSender = FrameSender(prioritizer, connection.pool, configuration.payload.maxFragmentSize)
        val streamsStorage = StreamsStorage(isServer, connection.pool)
        val keepAliveHandler = KeepAliveHandler(
            configuration.keepAlive.interval.toInt(DurationUnit.MILLISECONDS),
            configuration.keepAlive.maxLifetime.toInt(DurationUnit.MILLISECONDS),
            frameSender
        )

        val requestJob = SupervisorJob(connection.coroutineContext[Job])
        val requestContext = connection.coroutineContext + requestJob

        requestJob.invokeOnCompletion {
            prioritizer.close(it)
            streamsStorage.cleanup(it)
            configuration.close()
        }

        val requester =
            RSocketRequester(
                requestContext + CoroutineName("rSocket-requester"),
                frameSender,
                streamsStorage,
                connection.pool
            ) as RSocket
        val requestHandler = responder ?: EmptyRSocket
        val responder = RSocketResponder(
            requestContext + CoroutineName("rSocket-responder"),
            frameSender,
            requestHandler
        )

        // start keepalive ticks
        (connection + CoroutineName("rSocket-connection-keep-alive")).launch {
            while (isActive) keepAliveHandler.tick()
        }

        // start sending frames to connection
        (connection + CoroutineName("rSocket-connection-send")).launch {
            while (isActive) connection.sendFrame(prioritizer.receive())
        }

        // start frame handling
        (connection + CoroutineName("rSocket-connection-receive")).launch {
            while (isActive) connection.receiveFrame { frame ->
                when (frame.streamId) {
                    0    -> when (frame) {
                        is MetadataPushFrame -> responder.handleMetadataPush(frame.metadata)
                        is ErrorFrame        -> connection.cancel("Error frame received on 0 stream", RSocketError(frame))
                        is KeepAliveFrame    -> keepAliveHandler.mark(frame)
                        is LeaseFrame        -> frame.close().also { error("lease isn't implemented") }
                        else                 -> frame.close()
                    }
                    else -> streamsStorage.handleFrame(frame, responder)
                }
            }
        }
        onConnect(connection)
        return requester
    }
}

internal class RSocketClientConnectContextImpl(
    session: RSocketSession,
    deferredRequester: Deferred<RSocket>,
) : RSocketClientConnectContext, RSocketConnectContextImpl(session, deferredRequester) {
    override val isServer: Boolean get() = false
    override val configuration: RSocketClientConnectConfigurationImpl = RSocketClientConnectConfigurationImpl(this)

    override suspend fun onConnect(connection: Connection) {
        connection.sendFrame(
            SetupFrame(
                version = Version.Current,
                honorLease = false,
                keepAliveIntervalMillis = configuration.keepAlive.interval.toInt(DurationUnit.MILLISECONDS),
                keepAliveMaxLifetimeMillis = configuration.keepAlive.maxLifetime.toInt(DurationUnit.MILLISECONDS),
                resumeToken = null,
                metadataMimeTypeText = configuration.payload.mimeType.metadata.text,
                dataMimeTypeText = configuration.payload.mimeType.data.text,
                payload = configuration.setup.payload
            )
        )
    }
}

internal class RSocketServerConnectContextImpl(
    session: RSocketSession,
    deferredRequester: Deferred<RSocket>,
    keepAliveInterval: Duration,
    keepAliveMaxLifetime: Duration,
    metadataMimeType: MimeType.WithName,
    dataMimeType: MimeType.WithName,
    setupPayload: Payload,
) : RSocketServerConnectContext, RSocketConnectContextImpl(session, deferredRequester) {
    override val isServer: Boolean get() = true
    override val configuration: RSocketServerConnectConfigurationImpl = RSocketServerConnectConfigurationImpl(
        configurationState = this,
        keepAliveInterval = keepAliveInterval,
        keepAliveMaxLifetime = keepAliveMaxLifetime,
        metadataMimeType = metadataMimeType,
        dataMimeType = dataMimeType,
        setupPayload = setupPayload
    )

    override suspend fun onConnect(connection: Connection) {
    }
}
