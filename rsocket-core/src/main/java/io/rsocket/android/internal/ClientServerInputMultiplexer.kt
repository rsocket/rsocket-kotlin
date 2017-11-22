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
import io.rsocket.android.FrameType
import io.rsocket.android.plugins.DuplexConnectionInterceptor.Type
import io.rsocket.android.plugins.PluginRegistry
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

/**
 * [DuplexConnection.receive] is a single stream on which the following type of frames
 * arrive:
 *
 *
 *  * Frames for streams initiated by the initiator of the connection (client).
 *  * Frames for streams initiated by the acceptor of the connection (server).
 *
 *
 *
 * The only way to differentiate these two frames is determining whether the stream Id is odd or
 * even. Even IDs are for the streams initiated by server and odds are for streams initiated by the
 * client.
 */
class ClientServerInputMultiplexer(val source: DuplexConnection, plugins: PluginRegistry) {

    private val streamZeroConnection: DuplexConnection
    private val serverConnection: DuplexConnection
    private val clientConnection: DuplexConnection

    init {
        var s = source
        val streamZero = BehaviorProcessor.create<Flowable<Frame>>()
        val server = BehaviorProcessor.create<Flowable<Frame>>()
        val client = BehaviorProcessor.create<Flowable<Frame>>()

        s = plugins.applyConnection(Type.SOURCE, s)
        streamZeroConnection = plugins.applyConnection(Type.STREAM_ZERO, InternalDuplexConnection(s, streamZero))
        serverConnection = plugins.applyConnection(Type.SERVER, InternalDuplexConnection(s, server))
        clientConnection = plugins.applyConnection(Type.CLIENT, InternalDuplexConnection(s, client))

        s.receive()
                .groupBy { frame ->
                    val streamId = frame.streamId
                    val type: Type
                    if (streamId == 0) {
                        if (frame.type === FrameType.SETUP) {
                            type = Type.STREAM_ZERO
                        } else {
                            type = Type.CLIENT
                        }
                    } else if (streamId and 1 == 0) {
                        type = Type.SERVER
                    } else {
                        type = Type.CLIENT
                    }
                    type
                }
                .subscribe { group ->
                    when (group.key) {
                        Type.STREAM_ZERO -> streamZero.onNext(group)
                        Type.SERVER -> server.onNext(group)
                        Type.CLIENT -> client.onNext(group)
                    }
                }
    }

    fun asServerConnection(): DuplexConnection {
        return serverConnection
    }

    fun asClientConnection(): DuplexConnection {
        return clientConnection
    }

    fun asStreamZeroConnection(): DuplexConnection {
        return streamZeroConnection
    }

    fun close(): Completable {
        return source.close()
    }

    private class InternalDuplexConnection(private val source: DuplexConnection,
                                           p: Flowable<Flowable<Frame>>) : DuplexConnection {
        private val debugEnabled: Boolean = LOGGER.isDebugEnabled
        private val processor = p.firstOrError()
        override fun send(frames: Publisher<Frame>): Completable {
            var frame = frames
            if (debugEnabled) {
                frame = Flowable.fromPublisher(frame).doOnNext { f -> LOGGER.debug("sending -> " + f.toString()) }
            }

            return source.send(frame)
        }

        override fun sendOne(frame: Frame): Completable {
            if (debugEnabled) {
                LOGGER.debug("sending -> " + frame.toString())
            }
            return source.sendOne(frame)
        }

        override fun receive(): Flowable<Frame> {
            return processor.flatMapPublisher { f ->
                if (debugEnabled) {
                    return@flatMapPublisher f.doOnNext { frame -> LOGGER.debug("receiving -> " + frame.toString()) }
                } else {
                    return@flatMapPublisher f
                }
            }
        }

        override fun close(): Completable {
            return source.close()
        }

        override fun onClose(): Completable {
            return source.onClose()
        }

        override fun availability(): Double {
            return source.availability()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("io.rsocket.FrameLogger")
    }
}
