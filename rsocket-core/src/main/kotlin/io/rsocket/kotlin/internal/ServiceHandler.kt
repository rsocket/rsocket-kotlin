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

import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType

internal abstract class ServiceHandler(private val serviceConnection: DuplexConnection,
                                       private val errorConsumer: (Throwable) -> Unit) {

    internal val sentFrames = UnicastProcessor.create<Frame>()

    init {
        serviceConnection
                .receive()
                .subscribe(::handle, errorConsumer)

        serviceConnection
                .send(sentFrames)
                .subscribe({}, errorConsumer)
    }

    private fun handle(frame: Frame) {
        try {
            when (frame.type) {
                FrameType.LEASE -> handleLease(frame)
                FrameType.ERROR -> handleError(frame)
                FrameType.KEEPALIVE -> handleKeepAlive(frame)
                else -> handleUnknownFrame(frame)
            }
        } finally {
            frame.release()
        }
    }

    protected abstract fun handleKeepAlive(frame: Frame)

    @Suppress("UNUSED_PARAMETER")
    private fun handleLease(frame: Frame) {
        /*Lease interceptors processed frame already, just release it here*/
    }

    private fun handleError(frame: Frame) {
        errorConsumer(Exceptions.from(frame))
        serviceConnection.close().subscribe({}, errorConsumer)

    }

    private fun handleUnknownFrame(frame: Frame) {
        errorConsumer(IllegalArgumentException("Unexpected frame: $frame"))
    }
}