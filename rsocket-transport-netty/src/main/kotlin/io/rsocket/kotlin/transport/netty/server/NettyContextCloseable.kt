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

package io.rsocket.kotlin.transport.netty.server

import io.reactivex.Completable
import io.rsocket.kotlin.Closeable
import io.rsocket.kotlin.transport.netty.toCompletable
import reactor.ipc.netty.NettyContext
import java.net.InetSocketAddress

/**
 * A [Closeable] wrapping a [NettyContext], allowing for close and aware of its address.
 */
class NettyContextCloseable internal constructor(private val context: NettyContext)
    : Closeable {
    override fun close(): Completable =
            Completable.fromRunnable {
                if (!context.isDisposed) {
                    context.channel().close()
                }
            }

    override fun onClose(): Completable = context.onClose().toCompletable()

    /**
     * @see NettyContext.address
     * @return socket address.
     */
    fun address(): InetSocketAddress = context.address()
}
