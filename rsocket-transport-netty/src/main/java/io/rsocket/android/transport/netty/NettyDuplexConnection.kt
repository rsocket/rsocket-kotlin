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

import io.reactivex.Completable
import io.reactivex.Flowable
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import org.reactivestreams.Publisher
import reactor.ipc.netty.NettyContext
import reactor.ipc.netty.NettyInbound
import reactor.ipc.netty.NettyOutbound

class NettyDuplexConnection(private val inbound: NettyInbound,
                            private val outbound: NettyOutbound,
                            private val context: NettyContext) : DuplexConnection {

    override fun send(frame: Publisher<Frame>): Completable =
            Flowable.fromPublisher(frame)
                    .concatMap { sendOne(it).toFlowable<Frame>() }
                    .ignoreElements()

    override fun sendOne(frame: Frame): Completable =
            outbound.sendObject(frame.content()).then().toCompletable()

    override fun receive(): Flowable<Frame> =
            inbound.receive()
                    .map { buf -> Frame.from(buf.retain()) }
                    .toFlowable()

    override fun availability(): Double = if (context.isDisposed) 0.0 else 1.0

    override fun close(): Completable =
            Completable.fromRunnable {
                if (!context.isDisposed) {
                    context.channel().close()
                }
            }

    override fun onClose(): Completable =
            context.onClose().toCompletable()
}
