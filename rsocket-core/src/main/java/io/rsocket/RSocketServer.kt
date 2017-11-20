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

package io.rsocket

import io.netty.buffer.Unpooled
import io.netty.util.collection.IntObjectHashMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import io.rsocket.Frame.Request.initialRequestN
import io.rsocket.frame.FrameHeaderFlyweight.FLAGS_C
import io.rsocket.frame.FrameHeaderFlyweight.FLAGS_M
import io.rsocket.internal.LimitedRequestPublisher
import io.rsocket.util.PayloadImpl
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription

/** Server side RSocket. Receives [Frame]s from a [RSocketClient]  */
internal class RSocketServer(
        private val connection: DuplexConnection,
        private val requestHandler: RSocket,
        private val errorConsumer: (Throwable) -> Unit) : RSocket {

    private val sendingSubscriptions: IntObjectHashMap<DisposableSubscription> = IntObjectHashMap()

    private val sendProcessor: PublishProcessor<Frame> = PublishProcessor.create()
    private val receiveDisposable: Disposable

    init {

        // DO NOT Change the order here. The Send processor must be subscribed to before receiving connections

        connection
                .send(sendProcessor)
                .doOnError { handleSendProcessorError(it) }
                .doFinally { handleSendProcessorCancel() }
                .subscribe()

        this.receiveDisposable = connection
                .receive()
                .flatMapCompletable { handleFrame(it) }
                .doOnError(errorConsumer)
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
        val values = synchronized(this) {
            sendingSubscriptions.values
        }

        for (subscription in values) {
            try {
                subscription.cancel()
            } catch (e: Throwable) {
                errorConsumer(e)
            }

        }
    }

    private fun handleSendProcessorCancel() {
        val values = synchronized(this) {
            sendingSubscriptions.values
        }

        for (subscription in values) {
            try {
                subscription.cancel()
            } catch (e: Throwable) {
                errorConsumer(e)
            }

        }
    }

    override fun fireAndForget(payload: Payload): Completable {
        try {
            return requestHandler.fireAndForget(payload)
        } catch (t: Throwable) {
            return Completable.error(t)
        }

    }

    override fun requestResponse(payload: Payload): Single<Payload> {
        try {
            return requestHandler.requestResponse(payload)
        } catch (t: Throwable) {
            return Single.error(t)
        }

    }

    override fun requestStream(payload: Payload): Flowable<Payload> {
        try {
            return requestHandler.requestStream(payload)
        } catch (t: Throwable) {
            return Flowable.error(t)
        }

    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return Flowable.error(UnsupportedOperationException("not implemented"))
    }

    override fun metadataPush(payload: Payload): Completable {
        try {
            return requestHandler.metadataPush(payload)
        } catch (t: Throwable) {
            return Completable.error(t)
        }

    }

    override fun close(): Completable {
        return connection.close()
    }

    override fun onClose(): Completable {
        return connection.onClose()
    }

    private fun cleanup() {
        cleanUpSendingSubscriptions()
        requestHandler.close().subscribe()
    }

    @Synchronized private fun cleanUpSendingSubscriptions() {
        sendingSubscriptions.values.forEach({ it.cancel() })
        sendingSubscriptions.clear()
    }

    private fun handleFrame(frame: Frame): Completable {
        try {
            val streamId = frame.streamId
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
                .doOnSubscribe { subscription -> addSubscription(streamId, DisposableSubscription.disposable(subscription)) }
                .doOnError(errorConsumer)
                .doFinally { removeSubscription(streamId) }
    }

    private fun handleRequestResponse(streamId: Int, response: Single<Payload>): Completable {
        return response
                .doOnSubscribe { subscription -> addSubscription(streamId, DisposableSubscription.disposable(subscription)) }
                .map { payload ->
                    var flags = FLAGS_C
                    if (payload.hasMetadata()) {
                        flags = Frame.setFlag(flags, FLAGS_M)
                    }
                    Frame.PayloadFrame.from(streamId, FrameType.NEXT_COMPLETE, payload, flags)
                }
                .doOnError(errorConsumer)
                .onErrorResumeNext { t -> Single.just(Frame.Error.from(streamId, t)) }
                .doOnSuccess { sendProcessor.onNext(it) }
                .doFinally { removeSubscription(streamId) }
                .toCompletable()
    }

    private fun handleStream(streamId: Int, response: Flowable<Payload>, initialRequestN: Int): Completable {
        response
                .map { payload -> Frame.PayloadFrame.from(streamId, FrameType.NEXT, payload) }
                .compose { frameFlux ->
                    val frames = LimitedRequestPublisher.wrap(frameFlux)
                    synchronized(this@RSocketServer) {
                        sendingSubscriptions.put(streamId, DisposableSubscription.subscription(frames))
                    }
                    frames.increaseRequestLimit(initialRequestN.toLong())
                    frames
                }
                .concatWith(Flowable.just(Frame.PayloadFrame.from(streamId, FrameType.COMPLETE)))
                .onErrorResumeNext { t:Throwable -> Flowable.just(Frame.Error.from(streamId, t)) }
                .doOnNext { sendProcessor.onNext(it) }
                .doFinally { removeSubscription(streamId) }
                .subscribe()

        return Completable.complete()
    }

    private fun handleChannel(streamId: Int, firstFrame: Frame): Completable {
        return Completable.error(UnsupportedOperationException("Not implemented"))
    }

    private fun handleKeepAliveFrame(frame: Frame): Completable {
        return Completable.fromRunnable {
            if (Frame.Keepalive.hasRespondFlag(frame)) {
                val data = Unpooled.wrappedBuffer(frame.data)
                sendProcessor.onNext(Frame.Keepalive.from(data, false))
            }
        }
    }

    private fun handleCancelFrame(streamId: Int): Completable {
        return Completable.fromRunnable {
            var subscription: Disposable? = null
            synchronized(this) {
                subscription = sendingSubscriptions.remove(streamId)
            }
            subscription?.dispose()
        }
    }

    private fun handleError(streamId: Int, t: Throwable): Completable {
        return Completable.fromRunnable {
            errorConsumer(t)
            sendProcessor.onNext(Frame.Error.from(streamId, t))
        }
    }

    private fun handleRequestN(streamId: Int, frame: Frame): Completable {
        val subscription = getSubscription(streamId)
        if (subscription != null) {
            val n = Frame.RequestN.requestN(frame)
            subscription.request(if (n >= Integer.MAX_VALUE) java.lang.Long.MAX_VALUE else n.toLong())
        }
        return Completable.complete()
    }

    @Synchronized private fun addSubscription(streamId: Int, subscription: DisposableSubscription) {
        sendingSubscriptions.put(streamId, subscription)
    }

    @Synchronized private fun getSubscription(streamId: Int): DisposableSubscription? {
        return sendingSubscriptions.get(streamId)
    }

    @Synchronized private fun removeSubscription(streamId: Int) {
        sendingSubscriptions.remove(streamId)
    }

    internal sealed class DisposableSubscription:Disposable,Subscription {
        private class Disp(val d:Disposable):DisposableSubscription() {
            override fun isDisposed(): Boolean  = d.isDisposed

            override fun dispose() = d.dispose()

            override fun cancel() = dispose()

            override fun request(n: Long) {
            }
        }

        private class Subs(val s:Subscription):DisposableSubscription() {
            @Volatile private var isDisposed = false;

            override fun isDisposed(): Boolean  = isDisposed

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
