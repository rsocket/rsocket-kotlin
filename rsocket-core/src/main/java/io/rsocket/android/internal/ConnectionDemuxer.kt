/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.android.internal

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.FrameType.*
import io.rsocket.android.FrameType.SETUP
import io.rsocket.android.plugins.DuplexConnectionInterceptor.Type
import io.rsocket.android.plugins.DuplexConnectionInterceptor.Type.*
import io.rsocket.android.plugins.InterceptorRegistry
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

internal class ServerConnectionDemuxer(source: DuplexConnection,
                                       plugins: InterceptorRegistry)
    : ConnectionDemuxer(source, plugins) {

    override fun demux(frame: Frame): Type {
        val streamId = frame.streamId
        val type = frame.type
        return when {
            (type == SETUP) or (type == RESUME) -> Type.SETUP
            (streamId > 0) -> if ((streamId and 1 == 1)) RESPONDER else REQUESTER
            (type == METADATA_PUSH) -> RESPONDER
            else -> SERVICE
        }
    }
}

internal class ClientConnectionDemuxer(source: DuplexConnection,
                                       plugins: InterceptorRegistry)
    : ConnectionDemuxer(source, plugins) {

    override fun demux(frame: Frame): Type {
        val streamId = frame.streamId
        val type = frame.type
        return when {
            (type == SETUP) or (type == RESUME) -> Type.SETUP
            streamId > 0 -> if ((streamId and 1 == 1)) REQUESTER else RESPONDER
            (type == METADATA_PUSH) -> RESPONDER
            else -> SERVICE
        }
    }
}

sealed class ConnectionDemuxer(private val source: DuplexConnection,
                               plugins: InterceptorRegistry) {

    private val setupConnection: DuplexConnection
    private val responderConnection: DuplexConnection
    private val requesterConnection: DuplexConnection
    private val serviceConnection: DuplexConnection

    init {
        val src = plugins.interceptConnection(ALL, source)

        val setupConn = DemuxedConnection(src)
        setupConnection = plugins.interceptConnection(Type.SETUP, setupConn)

        val requesterConn = DemuxedConnection(src)
        requesterConnection = plugins.interceptConnection(REQUESTER, requesterConn)

        val responderConn = DemuxedConnection(src)
        responderConnection = plugins.interceptConnection(RESPONDER, responderConn)

        val serviceConn = DemuxedConnection(src)
        serviceConnection = plugins.interceptConnection(SERVICE, serviceConn)

        src.receive()
                .groupBy(::demux)
                .subscribe(
                        { inboundFrames ->
                            val type = inboundFrames.key
                            val connection = when (type) {
                                Type.SETUP -> setupConn
                                RESPONDER -> responderConn
                                REQUESTER -> requesterConn
                                SERVICE -> serviceConn
                                else -> allNotSupported(type)
                            }
                            connection.accept(inboundFrames)
                        },
                        { /*noop - errors are handled by demuxed frame streams*/ })
    }

    abstract fun demux(frame: Frame): Type

    fun responderConnection(): DuplexConnection = responderConnection

    fun requesterConnection(): DuplexConnection = requesterConnection

    fun setupConnection(): DuplexConnection = setupConnection

    fun serviceConnection(): DuplexConnection = serviceConnection

    fun close(): Completable = source.close()

    private fun allNotSupported(type: Type?): DemuxedConnection {
        throw IllegalStateException("demuxer does not " +
                "support frames of type: $type")
    }

    private class DemuxedConnection(private val sender: DuplexConnection)
        : DuplexConnection {
        private val debugEnabled: Boolean = LOGGER.isDebugEnabled
        private val receivers = BehaviorProcessor.create<Flowable<Frame>>()
        private val receiver = receivers.firstOrError()
        override fun send(frame: Publisher<Frame>): Completable {
            val frames = if (debugEnabled) {
                Flowable.fromPublisher(frame)
                        .doOnNext { f -> LOGGER.debug("sending -> " + f.toString()) }
            } else {
                frame
            }
            return sender.send(frames)
        }

        fun accept(receiver: Flowable<Frame>) {
            receivers.onNext(receiver)
        }

        override fun sendOne(frame: Frame): Completable {
            if (debugEnabled) {
                LOGGER.debug("sending -> " + frame.toString())
            }
            return sender.sendOne(frame)
        }

        override fun receive(): Flowable<Frame> {
            return receiver.flatMapPublisher { f ->
                if (debugEnabled)
                    f.doOnNext { frame -> LOGGER.debug("receiving -> " + frame.toString()) }
                else f
            }
        }

        override fun close(): Completable = sender.close()

        override fun onClose(): Completable = sender.onClose()

        override fun availability(): Double = sender.availability()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("io.rsocket.FrameLogger")
    }
}

