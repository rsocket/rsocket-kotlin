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

package io.rsocket.transport.okhttp

import io.netty.buffer.ByteBufAllocator
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.Frame
import okhttp3.*
import okio.ByteString
import org.reactivestreams.Publisher
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean

internal class OkWebsocket(client: OkHttpClient,
                           request: Request) {
    private val isOpen = AtomicBoolean()
    @Volatile
    private var failErr: ClosedChannelException? = null
    private val defFailErr by lazy { ClosedChannelException() }
    private val connection = BehaviorProcessor.create<OkHttpWebSocketConnection>()
    private val frames = UnicastProcessor.create<Frame>()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            isOpen.set(true)
            connection.onNext(OkHttpWebSocketConnection(this@OkWebsocket))
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString) {
            val messageSize = bytes.size()
            val buffer = allocator.buffer(messageSize + frameLengthSize)

            buffer.writeByte(messageSize shr 16)
            buffer.writeByte(messageSize shr 8)
            buffer.writeByte(messageSize)
            buffer.writeBytes(bytes.toByteArray())

            frames.onNext(Frame.from(buffer))
        }

        override fun onClosed(webSocket: WebSocket?,
                              code: Int,
                              reason: String?) {
            if (isOpen.compareAndSet(true, false)) {
                connection.onComplete()
            }
        }

        override fun onFailure(webSocket: WebSocket,
                               t: Throwable,
                               response: Response?) {
            if (isOpen.compareAndSet(true, false)) {
                connection.onError(t)
                val closedChannelException = ClosedChannelException()
                closedChannelException.initCause(t)
                failErr = closedChannelException
                frames.onError(closedChannelException)
            }
        }
    }

    private val ws = client.newWebSocket(request, listener)

    fun onConnect(): Single<OkHttpWebSocketConnection> = connection
            .firstOrError()
            .doOnDispose { ws.cancel() }

    fun receive(): Flowable<Frame> = frames

    fun send(frames: Publisher<Frame>): Completable =
            Flowable.fromPublisher(frames)
                    .map { frame ->
                        val content = frame.content()
                        val contentByteString = ByteString.of(content.skipBytes(frameLengthSize).nioBuffer())
                        frame.release()
                        ws.sendOrThrowOnFailure(contentByteString)
                    }.ignoreElements()

    fun close(): Completable = Completable.create { e ->
        ws.close(normalClose, "close")
        e.onComplete()
    }

    fun onClose(): Completable = connection
            .onErrorResumeNext(Flowable.empty())
            .ignoreElements()

    internal fun isOpen(): Boolean = isOpen.get()

    private fun WebSocket.sendOrThrowOnFailure(bytes: ByteString) {
        if (!send(bytes)) {
            throw (failErr ?: defFailErr)
        }
    }

    companion object {
        private const val normalClose = 1000
        private const val frameLengthSize = 3
        private val allocator = ByteBufAllocator.DEFAULT
    }
}

