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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
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

internal class OkWebsocket(client: OkHttpClient,
                           request: Request) {
    @Volatile
    internal var isOpen = false
    @Volatile
    private var failErr: ClosedChannelException? = null
    private val defFailErr by lazy {
        noStacktrace(ClosedChannelException())
    }
    private val connection = BehaviorProcessor.create<OkHttpWebSocketConnection>()
    private val frames = UnicastProcessor.create<Frame>()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            isOpen = true
            connection.onNext(OkHttpWebSocketConnection(this@OkWebsocket))
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString) {
            val msgBuffer = Unpooled.wrappedBuffer(bytes.asByteBuffer())
            val frameBuffer = writeFrame(msgBuffer)

            frames.onNext(Frame.from(frameBuffer))
        }

        private fun writeFrame(msgBuffer: ByteBuf): ByteBuf {
            val msgSize = msgBuffer.readableBytes()
            val frameSize = msgSize + frameLengthSize
            val frameBuffer = Unpooled.buffer(frameSize, frameSize)

            frameBuffer.writeByte(msgSize shr 16)
            frameBuffer.writeByte(msgSize shr 8)
            frameBuffer.writeByte(msgSize)
            frameBuffer.writeBytes(msgBuffer)

            return frameBuffer
        }

        override fun onClosed(webSocket: WebSocket?,
                              code: Int,
                              reason: String?) {
            isOpen = false
            connection.onComplete()
        }

        override fun onFailure(webSocket: WebSocket,
                               t: Throwable,
                               response: Response?) {
            connection.onError(t)
            if (isOpen) {
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
                    .map { it.content() }
                    .map { it.skipBytes(frameLengthSize).slice().nioBuffer() }
                    .map { ByteString.of(it) }
                    .flatMapCompletable { ws.sendAsync(it) }

    fun close(): Completable = Completable.create { e ->
        ws.close(normalClose, "close")
        e.onComplete()
    }

    fun onClose(): Completable = connection
            .onErrorResumeNext(Flowable.empty())
            .ignoreElements()

    private fun WebSocket.sendAsync(bytes: ByteString): Completable =
            Completable.create { e ->
                if (send(bytes))
                    e.onComplete()
                else
                    e.onError(failErr ?: defFailErr)
            }

    companion object {
        private const val normalClose = 1000
        private const val frameLengthSize = 3

        private fun <T : Throwable> noStacktrace(ex: T): T {
            ex.stackTrace = arrayOf(StackTraceElement(
                    ex.javaClass.name,
                    "<init>",
                    null,
                    -1))
            return ex
        }

    }
}

