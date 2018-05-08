/*
 * Copyright 2015-2018 the original author or authors.
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
package io.rsocket.android.transport.netty

import io.netty.buffer.Unpooled.wrappedBuffer
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.reactivex.Completable
import io.reactivex.Flowable
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.frame.FrameHeaderFlyweight
import io.rsocket.android.frame.FrameHeaderFlyweight.FRAME_LENGTH_SIZE
import org.reactivestreams.Publisher
import reactor.ipc.netty.NettyContext
import reactor.ipc.netty.NettyInbound
import reactor.ipc.netty.NettyOutbound

/**
 * Implementation of a DuplexConnection for Websocket.
 *
 *
 * rsocket-java strongly assumes that each Frame is encoded with the length. This is not true for
 * message oriented transports so this must be specifically dropped from Frames sent and stitched
 * back on for frames received.
 */
class WebsocketDuplexConnection(private val inbound: NettyInbound,
                                private val outbound: NettyOutbound,
                                private val context: NettyContext) : DuplexConnection {

    override fun send(frame: Publisher<Frame>): Completable {
        return Flowable.fromPublisher(frame)
                .concatMap { sendOne(it).toFlowable<Frame>() }
                .ignoreElements()
    }

    override fun sendOne(frame: Frame): Completable {
        return outbound.sendObject(
                BinaryWebSocketFrame(
                        frame.content().skipBytes(FRAME_LENGTH_SIZE)))
                .then()
                .toCompletable()
    }

    override fun receive(): Flowable<Frame> {
        return inbound.receive()
                .map { buf ->
                    val composite = context.channel().alloc().compositeBuffer()
                    val length = wrappedBuffer(ByteArray(FRAME_LENGTH_SIZE))
                    FrameHeaderFlyweight.encodeLength(length, 0, buf.readableBytes())
                    composite.addComponents(true, length, buf.retain())
                    Frame.from(composite)
                }.toFlowable()
    }

    override fun availability(): Double = if (context.isDisposed) 0.0 else 1.0

    override fun close(): Completable =
            Completable.fromRunnable {
                if (!context.isDisposed) {
                    context.channel().close()
                }
            }

    override fun onClose(): Completable = context.onClose().toCompletable()
}
