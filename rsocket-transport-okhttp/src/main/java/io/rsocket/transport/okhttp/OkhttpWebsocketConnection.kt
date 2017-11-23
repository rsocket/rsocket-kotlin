package io.rsocket.transport.okhttp

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.frame.FrameHeaderFlyweight.*
import io.rsocket.android.util.ExceptionUtil.noStacktrace
import okhttp3.*
import okio.ByteString
import org.reactivestreams.Publisher
import java.nio.channels.ClosedChannelException

/**
 * Created by Maksym Ostroverkhov on 27.10.17.
 */
internal class OkWebsocket(scheme: String, host: String, port: Int) {

    @Volatile private var isOpen = false
    @Volatile private var failErr: ClosedChannelException? = null
    private val defFailErr by lazy {
        noStacktrace(ClosedChannelException())
    }
    private val connection = BehaviorProcessor.create<OkHttpWebsocketConnection>()
    private val frames = UnicastProcessor.create<Frame>()
    private val url = HttpUrl.Builder().scheme(scheme).host(host).port(port).build()
    private val req = Request.Builder().url(url).build()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            isOpen = true
            connection.onNext(OkHttpWebsocketConnection(this@OkWebsocket))
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString) {
            val msgBuffer = Unpooled.wrappedBuffer(bytes.asByteBuffer())
            val frameBuffer = writeFrame(msgBuffer)

            frames.onNext(Frame.from(frameBuffer))
        }

        private fun writeFrame(msgBuffer: ByteBuf): ByteBuf {
            val msgSize = msgBuffer.readableBytes()
            val frameSize = msgSize + FRAME_LENGTH_SIZE
            val frameBuffer = Unpooled.buffer(frameSize, frameSize)

            frameBuffer.writeByte(msgSize shr 16)
            frameBuffer.writeByte(msgSize shr 8)
            frameBuffer.writeByte(msgSize)
            frameBuffer.writeBytes(msgBuffer)

            return frameBuffer
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            isOpen = false
            connection.onComplete()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connection.onError(t)
            if (isOpen) {
                val closedChannelException = ClosedChannelException()
                closedChannelException.initCause(t)
                failErr = closedChannelException
                frames.onError(closedChannelException)
            }
        }
    }

    private val ws = OkHttpClient().newWebSocket(req, listener)

    fun connected(): Single<OkHttpWebsocketConnection> = connection
            .firstOrError()
            .doOnDispose { ws.cancel() }

    fun receive(): Flowable<Frame> = frames

    fun send(frames: Publisher<Frame>): Completable =
            Flowable.fromPublisher(frames)
                    .map { it.content() }
                    .map { it.skipBytes(FRAME_LENGTH_SIZE).slice().nioBuffer() }
                    .map { ByteString.of(it) }
                    .flatMapCompletable { ws.sendAsync(it) }

    fun close() = Completable.create { e ->
        ws.close(NORMAL_CLOSE, "close")
        e.onComplete()
    }

    fun onClose(): Completable = connection
            .onErrorResumeNext(Flowable.empty())
            .ignoreElements()

    internal fun isClosed() = isOpen

    private fun WebSocket.sendAsync(bytes: ByteString): Completable =
            Completable.create { e ->
                val succ = send(bytes)
                if (succ) e.onComplete() else {
                    val throwable: Throwable = failErr ?: defFailErr
                    e.onError(throwable)
                }
            }

    companion object {
        val NORMAL_CLOSE = 1000
    }
}

class OkHttpWebsocketConnection internal constructor(private val ws: OkWebsocket) : DuplexConnection {

    override fun availability(): Double = if (ws.isClosed()) 0.0 else 1.0

    override fun close() = ws.close()

    override fun onClose() = ws.onClose()

    override fun send(frame: Publisher<Frame>) = ws.send(frame)

    override fun receive(): Flowable<Frame> = ws.receive()
}