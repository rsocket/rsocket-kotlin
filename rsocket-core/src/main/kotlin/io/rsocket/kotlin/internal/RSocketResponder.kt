/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal

import io.reactivex.*
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.*
import io.rsocket.kotlin.Frame.Request.initialRequestN
import io.rsocket.kotlin.internal.RSocketResponder.DisposableSubscription.Companion.subscription
import io.rsocket.kotlin.exceptions.ApplicationException
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_C
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_M
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.internal.util.reactiveStreamsRequestN
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


/** Responder side RSocket. Receives [Frame]s from a [RSocketRequester]  */
internal class RSocketResponder(
        private val connection: DuplexConnection,
        private val requestHandler: RSocket,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamRequestLimit: Int) : RSocket {

    private val completion = Lifecycle()
    private val sendingSubscriptions =
            ConcurrentHashMap<Int, Subscription>(256)
    private val channelReceivers =
            ConcurrentHashMap<Int, Subscriber<Payload>>(256)
    private val frameSender = FrameSender()
    private val receiveDisposable: Disposable

    init {
        connection
                .send(frameSender.sent())
                .subscribe({}, { completion.error(it) })

        receiveDisposable = connection
                .receive()
                .subscribe({ handleFrame(it) }, { completion.error(it) })

        connection
                .onClose()
                .subscribe({ completion.complete() }, errorConsumer)

        requestHandler
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
            requestHandler.requestStream(payload).rebatchRequests(streamRequestLimit)
        } catch (t: Throwable) {
            Flowable.error(t)
        }
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return try {
            requestHandler.requestChannel(
                    Flowable.fromPublisher(payloads).rebatchRequests(streamRequestLimit)
            ).rebatchRequests(streamRequestLimit)
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

    private fun handleFrame(frame: Frame) {
        try {
            val streamId = frame.streamId
            when (frame.type) {
                FrameType.FIRE_AND_FORGET -> handleFireAndForget(streamId, fireAndForget(DefaultPayload(frame)))
                FrameType.REQUEST_RESPONSE -> handleRequestResponse(streamId, requestResponse(DefaultPayload(frame)))
                FrameType.CANCEL -> handleCancel(streamId)
                FrameType.REQUEST_N -> handleRequestN(streamId, frame)
                FrameType.REQUEST_STREAM -> handleStream(streamId, requestStream(DefaultPayload(frame)), initialRequestN(frame))
                FrameType.REQUEST_CHANNEL -> handleChannel(streamId, frame)
                FrameType.METADATA_PUSH -> handleMetadataPush(metadataPush(DefaultPayload(frame)))
                FrameType.NEXT -> handleNext(streamId, frame)
                FrameType.COMPLETE -> handleComplete(streamId)
                FrameType.ERROR -> handleError(streamId, frame)
                FrameType.NEXT_COMPLETE -> handleNextComplete(streamId, frame)
                else -> handleUnsupportedFrame(frame)
            }
        } finally {
            frame.release()
        }
    }

    private fun handleUnsupportedFrame(frame: Frame) {
        errorConsumer(IllegalArgumentException("Unsupported frame: $frame"))
    }

    private fun handleNextComplete(streamId: Int, frame: Frame) {
        val receiver = channelReceivers[streamId]
        receiver?.onNext(DefaultPayload(frame))
        receiver?.onComplete()
    }

    private fun handleError(streamId: Int, frame: Frame) {
        val receiver = channelReceivers[streamId]
        receiver?.onError(ApplicationException(Frame.Error.message(frame)))
    }

    private fun handleComplete(streamId: Int) {
        val receiver = channelReceivers[streamId]
        receiver?.onComplete()
    }

    private fun handleNext(streamId: Int, frame: Frame) {
        val receiver = channelReceivers[streamId]
        receiver?.onNext(DefaultPayload(frame))
    }

    private fun handleFireAndForget(streamId: Int,
                                    result: Completable) {
        result.subscribe(object : CompletableObserver {
            override fun onComplete() {
                sendingSubscriptions -= streamId
            }

            override fun onSubscribe(d: Disposable) {
                sendingSubscriptions[streamId] = subscription(d)
            }

            override fun onError(e: Throwable) {
                sendingSubscriptions -= streamId
                errorConsumer(e)
            }
        })
    }

    private fun handleRequestResponse(streamId: Int,
                                      response: Single<Payload>) {
        response.subscribe(object : SingleObserver<Payload> {

            override fun onSuccess(payload: Payload) {
                sendingSubscriptions -= streamId
                var flags = FLAGS_C
                if (payload.hasMetadata) {
                    flags = Frame.setFlag(flags, FLAGS_M)
                }
                frameSender.send(Frame.PayloadFrame.from(
                        streamId,
                        FrameType.NEXT_COMPLETE,
                        payload,
                        flags))
            }

            override fun onSubscribe(d: Disposable) {
                sendingSubscriptions[streamId] = subscription(d)
            }

            override fun onError(e: Throwable) {
                sendingSubscriptions -= streamId
                val frame = when (e) {
                    is NoSuchElementException -> Frame.PayloadFrame.from(streamId, FrameType.COMPLETE)
                    else -> Frame.Error.from(streamId, e)
                }
                frameSender.send(frame)
            }
        })
    }

    private fun handleStream(streamId: Int,
                             response: Flowable<Payload>,
                             initialRequestN: Int) {
        response
                .compose { frameFlux ->
                    val frames = RequestingPublisher.wrap(frameFlux)
                    sendingSubscriptions[streamId] = frames
                    frames.request(reactiveStreamsRequestN(initialRequestN))
                    frames
                }
                .subscribe(object : Subscriber<Payload> {

                    override fun onSubscribe(s: Subscription) {
                        s.request(Long.MAX_VALUE)
                    }

                    override fun onNext(payload: Payload) {
                        frameSender.send(Frame.PayloadFrame.from(streamId, FrameType.NEXT, payload))
                    }

                    override fun onComplete() {
                        sendingSubscriptions -= streamId
                        frameSender.send(Frame.PayloadFrame.from(streamId, FrameType.COMPLETE))
                    }

                    override fun onError(t: Throwable) {
                        sendingSubscriptions -= streamId
                        frameSender.send(Frame.Error.from(streamId, t))
                    }
                })
    }

    private fun handleChannel(streamId: Int, firstFrame: Frame) {
        val receiver = StreamReceiver.create()
        channelReceivers[streamId] = receiver

        val request = receiver
                .doOnRequestIfActive { request -> frameSender.send(Frame.RequestN.from(streamId, request)) }
                .doOnCancel { frameSender.send(Frame.Cancel.from(streamId)) }
                .doOnError { t -> frameSender.send(Frame.Error.from(streamId, t)) }
                .doFinally { channelReceivers -= streamId }

        receiver.onNext(DefaultPayload(firstFrame))

        handleStream(
                streamId,
                requestChannel(request),
                initialRequestN(firstFrame))
    }

    private fun handleMetadataPush(result: Completable) {
        result.subscribe(object : CompletableObserver {
            override fun onComplete() {
            }

            override fun onSubscribe(d: Disposable) {
            }

            override fun onError(e: Throwable) = errorConsumer(e)
        })
    }

    private fun handleCancel(streamId: Int) {
        val subscription = sendingSubscriptions.remove(streamId)
        subscription?.cancel()
    }

    private fun handleRequestN(streamId: Int, frame: Frame) {
        val subscription = sendingSubscriptions[streamId]
        subscription?.request(reactiveStreamsRequestN(Frame.RequestN.requestN(frame)))
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

                connection
                        .close()
                        .subscribe({}, errorConsumer)

                requestHandler
                        .close()
                        .subscribe({}, errorConsumer)

                cleanUp(sendingSubscriptions) { it.cancel() }
                cleanUp(channelReceivers) { it.onError(err) }
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
        private val closedException = ClosedChannelException()
    }
}
