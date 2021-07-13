/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.*
import kotlinx.coroutines.*

@OptIn(TransportApi::class)
internal suspend inline fun Connection.connect(
    isServer: Boolean,
    maxFragmentSize: Int,
    interceptors: Interceptors,
    connectionConfig: ConnectionConfig,
    acceptor: ConnectionAcceptor
): RSocket {
    val keepAliveHandler = KeepAliveHandler(connectionConfig.keepAlive)
    val prioritizer = Prioritizer()
    val frameSender = FrameSender(prioritizer, pool, maxFragmentSize)
    val streamsStorage = StreamsStorage(isServer, pool)
    val requestJob = SupervisorJob(job)

    requestJob.invokeOnCompletion {
        prioritizer.close(it)
        streamsStorage.cleanup(it)
        connectionConfig.setupPayload.release()
    }

    val requestScope = CoroutineScope(requestJob + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })
    val connectionScope = CoroutineScope(job + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })

    val requester = interceptors.wrapRequester(RSocketRequester(job, frameSender, streamsStorage, requestScope, pool))
    val requestHandler = interceptors.wrapResponder(
        with(interceptors.wrapAcceptor(acceptor)) {
            ConnectionAcceptorContext(connectionConfig, requester).accept()
        }
    )

    // link completing of connection and requestHandler
    job.invokeOnCompletion { requestHandler.job.cancel("Connection closed", it) }
    requestHandler.job.invokeOnCompletion { if (it != null) job.cancel("Request handler failed", it) }

    // start keepalive ticks
    connectionScope.launch {
        while (isActive) {
            keepAliveHandler.tick()
            prioritizer.send(KeepAliveFrame(true, 0, ByteReadPacket.Empty))
        }
    }

    // start sending frames to connection
    connectionScope.launch {
        while (isActive) {
            sendFrame(prioritizer.receive())
        }
    }

    // start frame handling
    connectionScope.launch {
        val rSocketResponder = RSocketResponder(frameSender, requestHandler, requestScope)
        while (isActive) {
            receiveFrame().closeOnError { frame ->
                when (frame.streamId) {
                    0 -> when (frame) {
                        is MetadataPushFrame -> rSocketResponder.handleMetadataPush(frame.metadata)
                        is ErrorFrame        -> job.cancel("Error frame received on 0 stream", frame.throwable)
                        is KeepAliveFrame    -> {
                            keepAliveHandler.mark()
                            if (frame.respond) prioritizer.send(KeepAliveFrame(false, 0, frame.data)) else Unit
                        }
                        is LeaseFrame        -> frame.release().also { error("lease isn't implemented") }
                        else                 -> frame.release()
                    }
                    else -> streamsStorage.handleFrame(frame, rSocketResponder)
                }
            }
        }
    }

    return requester
}
