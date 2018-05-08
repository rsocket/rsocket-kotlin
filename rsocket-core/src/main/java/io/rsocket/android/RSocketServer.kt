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

package io.rsocket.android

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.Frame.Request.initialRequestN
import io.rsocket.android.RSocketServer.DisposableSubscription.Companion.subscription
import io.rsocket.android.exceptions.ApplicationException
import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_C
import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M
import io.rsocket.android.internal.RequestingPublisher
import io.rsocket.android.internal.StreamReceiver
import io.rsocket.android.util.ExceptionUtil.noStacktrace
import io.rsocket.android.util.PayloadImpl
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


/** Server side RSocket. Receives [Frame]s from a [RSocketClient]  */
internal class RSocketServer(
        private val connection: DuplexConnection,
        private val requestHandler: RSocket,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamDemandLimit: Int) : RSocket {

    private val completion = Lifecycle()
    private val sendingSubscriptions =
            ConcurrentHashMap<Int, Subscription>(256)
    private val channelReceivers =
            ConcurrentHashMap<Int, Subscriber<Payload>>(256)
    private val sentFrames =
            UnicastProcessor
                    .create<Frame>()
                    .toSerialized()
    private val receiveDisposable: Disposable

    internal constructor(connection: DuplexConnection,
                         requestHandler: RSocket,
                         errorConsumer: (Throwable) -> Unit)
            : this(
            connection,
            requestHandler,
            errorConsumer,
            DEFAULT_STREAM_WINDOW)

    init {
        connection
                .send(sentFrames)
                .subscribe({}, { completion.error(it) })

        receiveDisposable = connection
                .receive()
                .concatMap { frame -> handleFrame(frame) }
                .subscribe({}, { completion.error(it) })

        connection
                .onClose()
                .subscribe({ completion.complete() }, errorConsumer)
    }

    override fun fireAndForget(payload: Payload): Completable {
        return try {
            requestHandler.fireAndForget(payload)
        } catch (t: Throwable) {
            Completable.error(t)
        }
    }

    override fun requestResponse(payload: Payload): Single<Payload> {
        return try {
            requestHandler.requestResponse(payload)
        } catch (t: Throwable) {
            Single.error(t)
        }
    }

    override fun requestStream(payload: Payload): Flowable<Payload> {
        return try {
            requestHandler.requestStream(payload).rebatchRequests(streamDemandLimit)
        } catch (t: Throwable) {
            Flowable.error(t)
        }
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return try {
            requestHandler.requestChannel(
                    Flowable.fromPublisher(payloads).rebatchRequests(streamDemandLimit)
            ).rebatchRequests(streamDemandLimit)
        } catch (t: Throwable) {
            Flowable.error(t)
        }
    }

    override fun metadataPush(payload: Payload): Completable {
        return try {
            requestHandler.metadataPush(payload)
        } catch (t: Throwable) {
            Completable.error(t)
        }
    }

    override fun close(): Completable = connection.close()

    override fun onClose(): Completable = connection.onClose()

    private fun handleFrame(frame: Frame): Flowable<Void> {
        return try {
            val streamId = frame.streamId
            when (frame.type) {
                FrameType.FIRE_AND_FORGET -> handleFireAndForget(streamId, fireAndForget(PayloadImpl(frame)))
                FrameType.REQUEST_RESPONSE -> handleRequestResponse(streamId, requestResponse(PayloadImpl(frame)))
                FrameType.CANCEL -> handleCancel(streamId)
                FrameType.KEEPALIVE -> handleKeepAlive(frame)
                FrameType.REQUEST_N -> handleRequestN(streamId, frame)
                FrameType.REQUEST_STREAM -> handleStream(streamId, requestStream(PayloadImpl(frame)), initialRequestN(frame))
                FrameType.REQUEST_CHANNEL -> handleChannel(streamId, frame)
                FrameType.METADATA_PUSH -> metadataPush(PayloadImpl(frame))
                FrameType.NEXT -> handleNext(streamId, frame)
                FrameType.COMPLETE -> handleComplete(streamId)
                FrameType.ERROR -> handleError(streamId, frame)
                FrameType.NEXT_COMPLETE -> handleNextComplete(streamId, frame)
                else -> Completable.complete()
            }.toFlowable()
        } finally {
            frame.release()
        }
    }

    private fun handleNextComplete(streamId: Int, frame: Frame): Completable {
        val receiver = channelReceivers[streamId]
        receiver?.onNext(PayloadImpl(frame))
        receiver?.onComplete()
        return Completable.complete()
    }

    private fun handleError(streamId: Int, frame: Frame): Completable {
        val receiver = channelReceivers[streamId]
        receiver?.onError(ApplicationException(Frame.Error.message(frame)))
        return Completable.complete()
    }

    private fun handleComplete(streamId: Int): Completable {
        val receiver = channelReceivers[streamId]
        receiver?.onComplete()
        return Completable.complete()
    }

    private fun handleNext(streamId: Int, frame: Frame): Completable {
        val receiver = channelReceivers[streamId]
        receiver?.onNext(PayloadImpl(frame))
        return Completable.complete()
    }

    private fun handleFireAndForget(streamId: Int,
                                    result: Completable): Completable {
        return result
                .doOnSubscribe { d -> sendingSubscriptions[streamId] = subscription(d) }
                .doOnError(errorConsumer)
                .doFinally { sendingSubscriptions -= streamId }
    }

    private fun handleRequestResponse(streamId: Int,
                                      response: Single<Payload>): Completable {
        return response
                .doOnSubscribe { d -> sendingSubscriptions[streamId] = subscription(d) }
                .map { payload ->
                    var flags = FLAGS_C
                    if (payload.hasMetadata()) {
                        flags = Frame.setFlag(flags, FLAGS_M)
                    }
                    Frame.PayloadFrame.from(
                            streamId,
                            FrameType.NEXT_COMPLETE,
                            payload,
                            flags)
                }
                .doOnError(errorConsumer)
                .onErrorResumeNext { t -> Single.just(Frame.Error.from(streamId, t)) }
                .doOnSuccess { sentFrames.onNext(it) }
                .doFinally { sendingSubscriptions -= streamId }
                .ignoreElement()
    }

    private fun handleStream(streamId: Int,
                             response: Flowable<Payload>,
                             initialRequestN: Int): Completable {
        response
                .map { payload -> Frame.PayloadFrame.from(streamId, FrameType.NEXT, payload) }
                .compose { frameFlux ->
                    val frames = RequestingPublisher.wrap(frameFlux)
                    sendingSubscriptions[streamId] = frames
                    frames.request(initialRequestN.toLong())
                    frames
                }
                .concatWith(Flowable.just(Frame.PayloadFrame.from(streamId, FrameType.COMPLETE)))
                .onErrorResumeNext { t: Throwable -> Flowable.just(Frame.Error.from(streamId, t)) }
                .doFinally { sendingSubscriptions -= streamId }
                .subscribe { sentFrames.onNext(it) }
        return Completable.complete()
    }

    private fun handleChannel(streamId: Int, firstFrame: Frame): Completable {
        val receiver = StreamReceiver.create()
        channelReceivers[streamId] = receiver

        val request = receiver
                .doOnRequestIfActive { request -> sentFrames.onNext(Frame.RequestN.from(streamId, request)) }
                .doOnCancel { sentFrames.onNext(Frame.Cancel.from(streamId)) }
                .doOnError { t -> sentFrames.onNext(Frame.Error.from(streamId, t)) }
                .doFinally { channelReceivers -= streamId }

        receiver.onNext(PayloadImpl(firstFrame))

        return handleStream(
                streamId,
                requestChannel(request),
                initialRequestN(firstFrame))
    }

    private fun handleKeepAlive(frame: Frame): Completable {
        if (Frame.Keepalive.hasRespondFlag(frame)) {
            val data = Unpooled.wrappedBuffer(frame.data)
            sentFrames.onNext(Frame.Keepalive.from(data, false))
        }
        return Completable.complete()
    }

    private fun handleCancel(streamId: Int): Completable {
        val subscription = sendingSubscriptions.remove(streamId)
        subscription?.cancel()
        return Completable.complete()
    }

    private fun handleRequestN(streamId: Int, frame: Frame): Completable {
        val subscription = sendingSubscriptions[streamId]
        subscription?.let {
            val n = Frame.RequestN.requestN(frame).toLong()
            it.request(if (n >= Integer.MAX_VALUE) Long.MAX_VALUE else n)
        }
        return Completable.complete()
    }

    private inner class Lifecycle {
        private val completed = AtomicBoolean()

        fun complete() {
            completeOnce(closedException)
        }

        fun error(err: Throwable) {
            completeOnce(err)
        }

        private fun completeOnce(err: Throwable) {
            if (completed.compareAndSet(false, true)) {
                receiveDisposable.dispose()
                requestHandler
                        .close()
                        .subscribe({}, errorConsumer)

                cleanUp(sendingSubscriptions, { it.cancel() })
                cleanUp(channelReceivers, { it.onError(err) })
            }
        }

        private inline fun <T> cleanUp(map: MutableMap<Int, T>,
                                       action: (T) -> Unit) {
            map.values.forEach {
                try {
                    action(it)
                } catch (t: Throwable) {
                    errorConsumer(t)
                }
            }
            map.clear()
        }
    }

    internal sealed class DisposableSubscription : Disposable, Subscription {
        private class Impl(val d: Disposable) : DisposableSubscription() {
            override fun isDisposed(): Boolean = d.isDisposed

            override fun dispose() = d.dispose()

            override fun cancel() = dispose()

            override fun request(n: Long) {
            }
        }

        companion object {
            fun subscription(d: Disposable): DisposableSubscription = Impl(d)
        }
    }

    companion object {
        private const val DEFAULT_STREAM_WINDOW = 128
        private val closedException = noStacktrace(ClosedChannelException())
    }
}
