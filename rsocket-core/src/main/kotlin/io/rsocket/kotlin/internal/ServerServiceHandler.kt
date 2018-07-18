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

package io.rsocket.kotlin.internal

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.KeepAlive
import io.rsocket.kotlin.exceptions.ConnectionException
import java.util.concurrent.TimeUnit

internal class ServerServiceHandler(private val serviceConnection: DuplexConnection,
                                    keepAlive: KeepAlive,
                                    errorConsumer: (Throwable) -> Unit)
    : ServiceHandler(serviceConnection, errorConsumer) {

    @Volatile
    private var keepAliveReceivedMillis = System.currentTimeMillis()
    private var subscription: Disposable? = null

    init {
        val tickPeriod = keepAlive.keepAliveInterval().millis
        val timeout = keepAlive.keepAliveMaxLifeTime().millis
        subscription = Flowable.interval(tickPeriod, TimeUnit.MILLISECONDS)
                .concatMapCompletable { checkKeepAlive(timeout) }
                .subscribe({},
                        { err ->
                            errorConsumer(err)
                            serviceConnection.close().subscribe({}, errorConsumer)
                        })

        serviceConnection.onClose().subscribe({ cleanup() }, errorConsumer)
    }

    override fun handleKeepAlive(frame: Frame) {
        if (Frame.Keepalive.hasRespondFlag(frame)) {
            keepAliveReceivedMillis = System.currentTimeMillis()
            val data = Unpooled.wrappedBuffer(frame.data)
            sentFrames.onNext(Frame.Keepalive.from(data, false))
        }
    }

    private fun cleanup() {
        subscription?.dispose()
    }

    private fun checkKeepAlive(timeout: Long): Completable {
        return Completable.fromRunnable {
            val now = System.currentTimeMillis()
            val duration = now - keepAliveReceivedMillis
            if (duration > timeout) {
                val message = String.format(
                        "keep-alive timed out: %d of %d ms", duration, timeout)
                throw ConnectionException(message)
            }
        }
    }
}