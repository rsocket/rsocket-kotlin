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
import io.netty.util.collection.IntObjectHashMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.Frame.Request.initialRequestN
import io.rsocket.android.exceptions.ApplicationException
import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_C
import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M
import io.rsocket.android.internal.LimitableRequestPublisher
import io.rsocket.android.util.PayloadImpl
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription


/** Server side RSocket. Receives [Frame]s from a [RSocketClient]  */
 internal class RSocketServer(
        private val connection: DuplexConnection,
        private val requestHandler: RSocket,
        private val errorConsumer: (Throwable) -> Unit) : RSocket {

    private val sendingSubscriptions: IntObjectHashMap<Subscription> = IntObjectHashMap()
    private val channelProcessors: IntObjectHashMap<UnicastProcessor<Payload>> = IntObjectHashMap()

    private val sendProcessor: FlowableProcessor<Frame> = PublishProcessor.create<Frame>().toSerialized()
    private val receiveDisposable: Disposable

    init {

        // DO NOT Change the order here. The Send processor must be subscribed to before receiving
        // connections

        connection
                .send(sendProcessor)
                .doOnError { handleSendProcessorError(it) }
                .subscribe()

        this.receiveDisposable = connection
                .receive()
                .concatMapEager { frame ->
                    handleFrame(frame)
                            .onErrorResumeNext(
                                    { t: Throwable ->
                                        errorConsumer.invoke(t)
                                        Completable.complete()
                                    }).toFlowable<Void>()
                }
                .doOnError(errorConsumer)
                .ignoreElements()
                .subscribe()

        this.connection
                .onClose()
                .doOnError(errorConsumer)
                .doFinally {
                    cleanup()
                    receiveDisposable.dispose()
                }
                .subscribe()
    }

    private fun handleSendProcessorError(t: Throwable) {
        val (sendingSubscriptions, channelProcessors) = synchronized(this) {
            Pair(sendingSubscriptions.values, channelProcessors.values)
        }

        for (subscription in sendingSubscriptions) {
            try {
                subscription.cancel()
            } catch (e: Throwable) {
                errorConsumer(e)
            }

        }

        for (subscription in channelProcessors) {
            try {
                subscription.onError(t)
            } catch (e: Throwable) {
                errorConsumer(e)
            }
        }
    }

    override fun fireAndForget(payload: Payload): Completable {
        return try {
            requestHandler.fireAndForget(payload)
        } catch (t: Throwable) {
            Completable.complete()
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
            requestHandler.requestStream(payload)
        } catch (t: Throwable) {
            Flowable.error(t)
        }

    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return try {
            requestHandler.requestChannel(payloads)
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

    private fun cleanup() {
        cleanUpSendingSubscriptions()
        cleanUpChannelProcessors()

        requestHandler.close().subscribe()
    }

    @Synchronized private fun cleanUpSendingSubscriptions() {
        sendingSubscriptions.values.forEach { it.cancel() }
        sendingSubscriptions.clear()
    }

    @Synchronized private fun cleanUpChannelProcessors() {
        channelProcessors.values.forEach { it.onComplete() }
        channelProcessors.clear()
    }

    private fun handleFrame(frame: Frame): Completable {
        try {
            val streamId = frame.streamId
            val receiver: Subscriber<Payload>?
            when (frame.type) {
                FrameType.FIRE_AND_FORGET -> return handleFireAndForget(streamId, fireAndForget(PayloadImpl(frame)))
                FrameType.REQUEST_RESPONSE -> return handleRequestResponse(streamId, requestResponse(PayloadImpl(frame)))
                FrameType.CANCEL -> return handleCancelFrame(streamId)
                FrameType.KEEPALIVE -> return handleKeepAliveFrame(frame)
                FrameType.REQUEST_N -> return handleRequestN(streamId, frame)
                FrameType.REQUEST_STREAM -> return handleStream(
                        streamId, requestStream(PayloadImpl(frame)), initialRequestN(frame))
                FrameType.REQUEST_CHANNEL -> return handleChannel(streamId, frame)
                FrameType.PAYLOAD ->
                    // TODO: Hook in receiving socket.
                    return Completable.complete()
                FrameType.METADATA_PUSH -> return metadataPush(PayloadImpl(frame))
                FrameType.LEASE ->
                    // Lease must not be received here as this is the server end of the socket which sends
                    // leases.
                    return Completable.complete()
                FrameType.NEXT -> {
                    receiver = getChannelProcessor(streamId)
                    receiver?.onNext(PayloadImpl(frame))
                    return Completable.complete()
                }
                FrameType.COMPLETE -> {
                    receiver = getChannelProcessor(streamId)
                    receiver?.onComplete()
                    return Completable.complete()
                }
                FrameType.ERROR -> {
                    receiver = getChannelProcessor(streamId)
                    receiver?.onError(ApplicationException(Frame.Error.message(frame)))
                    return Completable.complete()
                }
                FrameType.NEXT_COMPLETE -> {
                    receiver = getChannelProcessor(streamId)
                    receiver?.onNext(PayloadImpl(frame))
                    receiver?.onComplete()

                    return Completable.complete()
                }

                FrameType.SETUP -> return handleError(
                        streamId, IllegalStateException("Setup frame received post setup."))
                else -> return handleError(
                        streamId,
                        IllegalStateException(
                                "ServerRSocket: Unexpected frame type: " + frame.type))
            }
        } finally {
            frame.release()
        }
    }

    private fun handleFireAndForget(streamId: Int, result: Completable): Completable {
        return result
                .doOnSubscribe { d -> addSubscription(streamId, DisposableSubscription.disposable(d)) }
                .doOnError(errorConsumer)
                .doFinally { removeSubscription(streamId) }
    }

    private fun handleRequestResponse(streamId: Int, response: Single<Payload>): Completable {
        return response
                .doOnSubscribe { d -> addSubscription(streamId, DisposableSubscription.disposable(d)) }
                .map(
                        { payload ->
                            var flags = FLAGS_C
                            if (payload.hasMetadata()) {
                                flags = Frame.setFlag(flags, FLAGS_M)
                            }
                            val frame = Frame.PayloadFrame.from(streamId, FrameType.NEXT_COMPLETE, payload, flags)
                            frame
                        })
                .doOnError(errorConsumer)
                .onErrorResumeNext { t -> Single.just(Frame.Error.from(streamId, t)) }
                .doAfterSuccess { sendProcessor.onNext(it) }
                .doFinally { removeSubscription(streamId) }
                .toCompletable()
    }

    private fun handleStream(streamId: Int, response: Flowable<Payload>, initialRequestN: Int): Completable {
        response
                .map({ payload ->
                    val frame = Frame.PayloadFrame.from(streamId, FrameType.NEXT, payload)
                    frame
                })
                .compose(
                        { frameFlux ->
                            val frames = LimitableRequestPublisher.wrap(frameFlux)
                            synchronized(this) {
                                sendingSubscriptions.put(streamId, frames)
                            }
                            frames.increaseRequestLimit(initialRequestN.toLong())
                            frames
                        })
                .concatWith(Flowable.just(Frame.PayloadFrame.from(streamId, FrameType.COMPLETE)))
                .onErrorResumeNext { t: Throwable -> Flowable.just(Frame.Error.from(streamId, t)) }
                .doOnNext({ sendProcessor.onNext(it) })
                .doFinally { removeSubscription(streamId) }
                .subscribe()

        return Completable.complete()
    }

    private fun handleChannel(streamId: Int, firstFrame: Frame): Completable {
        val frames = UnicastProcessor.create<Payload>()
        addChannelProcessor(streamId, frames)

        val payloads = frames
                .doOnCancel(
                        { sendProcessor.onNext(Frame.Cancel.from(streamId)) })
                .doOnError(
                        { t -> sendProcessor.onNext(Frame.Error.from(streamId, t)) })
                .doOnRequest(
                        { l -> sendProcessor.onNext(Frame.RequestN.from(streamId, l)) })
                .doFinally { removeChannelProcessor(streamId) }

        // not chained, as the payload should be enqueued in the Unicast processor before this method
        // returns
        // and any later payload can be processed
        frames.onNext(PayloadImpl(firstFrame))

        return handleStream(streamId, requestChannel(payloads), initialRequestN(firstFrame))
    }

    private fun handleKeepAliveFrame(frame: Frame): Completable {
        if (Frame.Keepalive.hasRespondFlag(frame)) {
            val data = Unpooled.wrappedBuffer(frame.data)
            sendProcessor.onNext(Frame.Keepalive.from(data, false))
        }
        return Completable.complete()
    }

    private fun handleCancelFrame(streamId: Int): Completable =
            Completable.fromRunnable {
                var subscription: Subscription? = null
                synchronized(this) {
                    subscription = sendingSubscriptions.remove(streamId)
                }
                subscription?.cancel()
            }

    private fun handleError(streamId: Int, t: Throwable): Completable =
            Completable.fromRunnable {
                errorConsumer(t)
                sendProcessor.onNext(Frame.Error.from(streamId, t))
            }

    private fun handleRequestN(streamId: Int, frame: Frame): Completable {
        val subscription = getSubscription(streamId)
        if (subscription != null) {
            val n = Frame.RequestN.requestN(frame).toLong()
            subscription.request(if (n >= Integer.MAX_VALUE) java.lang.Long.MAX_VALUE else n)
        }
        return Completable.complete()
    }

    @Synchronized private fun addSubscription(streamId: Int, subscription: Subscription) {
        sendingSubscriptions.put(streamId, subscription)
    }

    @Synchronized private fun getSubscription(streamId: Int): Subscription? =
            sendingSubscriptions.get(streamId)

    @Synchronized private fun removeSubscription(streamId: Int) {
        sendingSubscriptions.remove(streamId)
    }

    @Synchronized private fun addChannelProcessor(streamId: Int, processor: UnicastProcessor<Payload>) {
        channelProcessors.put(streamId, processor)
    }

    @Synchronized private fun getChannelProcessor(streamId: Int): UnicastProcessor<Payload>? =
            channelProcessors.get(streamId)

    @Synchronized private fun removeChannelProcessor(streamId: Int) {
        channelProcessors.remove(streamId)
    }

    internal sealed class DisposableSubscription : Disposable, Subscription {
        private class Disp(val d: Disposable) : DisposableSubscription() {
            override fun isDisposed(): Boolean = d.isDisposed

            override fun dispose() = d.dispose()

            override fun cancel() = dispose()

            override fun request(n: Long) {
            }
        }

        private class Subs(val s: Subscription) : DisposableSubscription() {
            @Volatile private var isDisposed = false

            override fun isDisposed(): Boolean = isDisposed

            override fun dispose() {
                isDisposed = true
                cancel()
            }

            override fun cancel() = s.cancel()

            override fun request(n: Long) = s.request(n)
        }

        companion object {
            fun disposable(d: Disposable): DisposableSubscription = Disp(d)

            fun subscription(s: Subscription): DisposableSubscription = Subs(s)
        }
    }

}
