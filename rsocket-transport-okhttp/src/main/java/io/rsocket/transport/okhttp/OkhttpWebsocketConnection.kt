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
import io.rsocket.android.frame.FrameHeaderFlyweight.FRAME_LENGTH_SIZE
import io.rsocket.android.frame.FrameHeaderFlyweight.encodeLength
import okhttp3.*
import okio.ByteString
import org.reactivestreams.Publisher

/**
 * Created by Maksym Ostroverkhov on 27.10.17.
 */
internal class OkWebsocket(scheme: String, host: String, port: Int) {

    @Volatile private var closed = true
    private val connected = BehaviorProcessor.create<OkHttpWebsocketConnection>()
    private val frames = UnicastProcessor.create<Frame>()
    private val url = HttpUrl.Builder().scheme(scheme).host(host).port(port).build()
    private val req = Request.Builder().url(url).build()
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            closed = false
            connected.onNext(OkHttpWebsocketConnection(this@OkWebsocket))
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString) {
            val messageByteBuf = Unpooled.wrappedBuffer(bytes.asByteBuffer())
            val composite = Unpooled.compositeBuffer()
            val lengthByteBuf = Unpooled.wrappedBuffer(ByteArray(FRAME_LENGTH_SIZE))
            encodeLength(lengthByteBuf, 0, messageByteBuf.readableBytes())
            composite.addComponents(true, lengthByteBuf, messageByteBuf.retain())
            frames.onNext(Frame.from(composite))
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            closed = true
            connected.onComplete()
        }

        override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) =
                (if (closed) connected else frames).onError(t)

    }
    private val ws = OkHttpClient().newWebSocket(req, listener)

    fun connected(): Single<OkHttpWebsocketConnection> = connected.firstOrError()

    fun receive(): Flowable<Frame> = frames

    fun send(frames: Publisher<Frame>): Completable =
            Flowable.fromPublisher(frames)
                    .map { it.content() }
                    .map { it.skipBytes(FRAME_LENGTH_SIZE).slice().toArray() }
                    .map { ByteString.of(*it) }
                    .flatMapCompletable { ws.sendAsync(it) }

    fun isClosed() = closed

    fun close() = Completable.create { e ->
        ws.close(NORMAL_CLOSE, "close")
        e.onComplete()
    }

    private fun ByteBuf.toArray(): ByteArray {
        val byteArray = ByteArray(readableBytes())
        val from = readerIndex()
        var index = 0
        for (i in from until from + readableBytes()) {
            byteArray[index++] = getByte(i)
        }
        return byteArray
    }

    fun onClose(): Completable = connected.ignoreElements()

    private fun WebSocket.sendAsync(bytes: ByteString): Completable =
            Completable.create { e ->
                send(bytes)
                e.onComplete()
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