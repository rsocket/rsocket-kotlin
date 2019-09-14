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

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.*
import io.rsocket.kotlin.exceptions.ApplicationException
import io.rsocket.kotlin.exceptions.ChannelRequestException
import io.rsocket.kotlin.internal.util.reactiveStreamsRequestN
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Requester Side of a RSocket. Sends [Frame]s to a [RSocketResponder]  */
internal class RSocketRequester(
        private val connection: DuplexConnection,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamIds: StreamIds,
        private val streamRequestLimit: Int) : RSocket {

    private val senders = ConcurrentHashMap<Int, Subscription>(256)
    private val receivers = ConcurrentHashMap<Int, Subscriber<Payload>>(256)
    private val lifecycle = Lifecycle()

    private val frameSender = FrameSender()

    init {
        connection
                .send(frameSender.sent())
                .subscribe(
                        {},
                        { lifecycle.error(it) })
        connection
                .receive()
                .subscribe(
                        { handleFrame(it) },
                        { lifecycle.error(it) })
        connection
                .onClose()
                .subscribe(
                        { lifecycle.complete() },
                        errorConsumer)
    }

    override fun fireAndForget(payload: Payload) =
            lifecycle.fireAndForget(handleFireAndForget(payload))

    override fun requestResponse(payload: Payload) =
            lifecycle.requestResponse(handleRequestResponse(payload))

    override fun requestStream(payload: Payload): Flowable<Payload> =
            lifecycle.requestStream(
                    handleRequestStream(payload).rebatchRequests(streamRequestLimit))

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
            lifecycle.requestChannel(
                    handleChannel(
                            Flowable.fromPublisher(payloads)
                                    .rebatchRequests(streamRequestLimit)
                    ).rebatchRequests(streamRequestLimit))

    override fun metadataPush(payload: Payload): Completable =
            lifecycle.metadataPush(handleMetadataPush(payload))

    override fun availability(): Double = connection.availability()

    override fun close(): Completable = connection.close()

    override fun onClose(): Completable = connection.onClose()

    private fun handleFireAndForget(payload: Payload): Completable {
        return Completable.fromRunnable {
            val streamId = streamIds.nextStreamId(receivers)
            val requestFrame = Frame.Request.from(
                    streamId,
                    FrameType.FIRE_AND_FORGET,
                    payload,
                    1)
            frameSender.send(requestFrame)
        }
    }

    private fun handleRequestResponse(payload: Payload): Single<Payload> {
        return Single.defer {
            val streamId = streamIds.nextStreamId(receivers)
            val requestFrame = Frame.Request.from(
                    streamId, FrameType.REQUEST_RESPONSE, payload, 1)

            val receiver = UnicastProcessor.create<Payload>()
            receivers[streamId] = receiver
            frameSender.send(requestFrame)

            receiver
                    .doOnCancel { frameSender.send(Frame.Cancel.from(streamId)) }
                    .doFinally { receivers -= streamId }
                    .firstOrError()
        }
    }

    private fun handleRequestStream(payload: Payload): Flowable<Payload> {
        return Flowable.defer {
            val streamId = streamIds.nextStreamId(receivers)
            val receiver = StreamReceiver.create()
            receivers[streamId] = receiver
            var isFirstRequestN = true

            receiver.doOnRequestIfActive { requestN ->
                val frame = if (isFirstRequestN) {
                    isFirstRequestN = false
                    Frame.Request.from(
                            streamId,
                            FrameType.REQUEST_STREAM,
                            payload,
                            requestN)
                } else {
                    Frame.RequestN.from(
                            streamId,
                            requestN)
                }
                frameSender.send(frame)
            }.doOnCancel {
                frameSender.send(Frame.Cancel.from(streamId))
            }.doFinally {
                receivers -= streamId
            }
        }
    }

    private fun handleChannel(request: Flowable<Payload>): Flowable<Payload> {
        return Flowable.defer {
            val receiver = StreamReceiver.create()
            val streamId = streamIds.nextStreamId(receivers)
            var firstReqN = true
            var firstReqPayload = true

            receiver.doOnRequestIfActive { requestN ->

                if (firstReqN) {
                    firstReqN = false

                    val requestFrames = request
                            .compose {
                                val sender = RequestingPublisher.wrap(it)
                                sender.request(1)
                                senders[streamId] = sender
                                receivers[streamId] = receiver
                                sender
                            }
                            .map { payload ->
                                if (firstReqPayload) {
                                    firstReqPayload = false
                                    Frame.Request.from(
                                            streamId,
                                            FrameType.REQUEST_CHANNEL,
                                            payload,
                                            requestN)
                                } else {
                                    Frame.PayloadFrame.from(
                                            streamId,
                                            FrameType.NEXT,
                                            payload)
                                }
                            }
                    requestFrames.subscribe(
                            ChannelRequestSubscriber(
                                    { payload -> frameSender.send(payload) },
                                    {
                                        receiver.onError(ChannelRequestException(
                                                "Channel request exception", it))
                                    },
                                    { empty ->
                                        if (empty) {
                                            receiver.onComplete()
                                        } else {
                                            frameSender.send(Frame.PayloadFrame.from(
                                                    streamId, FrameType.COMPLETE))
                                        }
                                    }))

                } else {
                    frameSender.send(Frame.RequestN.from(streamId, requestN))
                }
            }.doOnError { err ->
                if (err is ChannelRequestException) {
                    frameSender.send(Frame.Error.from(streamId,
                            ApplicationException(err.message, err.cause)))
                }
            }.doOnCancel {
                frameSender.send(Frame.Cancel.from(streamId))
            }.doFinally {
                receivers -= streamId
                senders.remove(streamId)?.cancel()
            }
        }
    }

    private fun handleMetadataPush(payload: Payload): Completable {
        return Completable.fromRunnable {
            val requestFrame = Frame.Request.from(
                    0,
                    FrameType.METADATA_PUSH,
                    payload,
                    1)
            frameSender.send(requestFrame)
        }
    }

    private fun handleFrame(frame: Frame) {
        try {
            handle(frame)
        } finally {
            frame.release()
        }
    }

    private fun handle(frame: Frame) {
        val streamId = frame.streamId
        val type = frame.type
        receivers[streamId]?.let { receiver ->
            when (type) {
                FrameType.ERROR -> {
                    receiver.onError(Exceptions.from(frame))
                }
                FrameType.NEXT_COMPLETE -> {
                    receiver.onNext(DefaultPayload(frame))
                    receiver.onComplete()
                }
                FrameType.CANCEL -> {
                    val sender = senders.remove(streamId)
                    sender?.cancel()
                }
                FrameType.NEXT -> receiver.onNext(DefaultPayload(frame))
                FrameType.REQUEST_N -> {
                    val sender = senders[streamId]
                    sender?.request(reactiveStreamsRequestN(Frame.RequestN.requestN(frame)))
                }
                FrameType.COMPLETE -> {
                    receiver.onComplete()
                }
                else -> unsupportedFrame(streamId, frame)
            }
        }
    }

    private fun unsupportedFrame(streamId: Int, frame: Frame) {
        errorConsumer(IllegalStateException(
                "Client received unsupported frame on stream " +
                        "$streamId : $frame"))
    }

    private inner class Lifecycle {
        private val terminated = AtomicReference<Throwable>()

        fun fireAndForget(request: Completable): Completable =
                call(request) { Completable.error(it) }

        fun requestResponse(request: Single<Payload>): Single<Payload> =
                call(request) { Single.error(it) }

        fun requestStream(request: Flowable<Payload>): Flowable<Payload> =
                call(request) { Flowable.error(it) }

        fun requestChannel(request: Flowable<Payload>): Flowable<Payload> =
                call(request) { Flowable.error(it) }

        fun metadataPush(request: Completable): Completable =
                call(request) { Completable.error(it) }

        private inline fun <T> call(arg: T, error: (Throwable) -> T) =
                terminated.get()?.let { error(it) } ?: arg

        fun complete() {
            terminateOnce(closedException)
        }

        fun error(err: Throwable) {
            terminateOnce(err)
        }

        private fun terminateOnce(err: Throwable) {
            if (terminated.compareAndSet(null, err)) {
                cleanUp(receivers) { it.onError(err) }
                cleanUp(senders) { it.cancel() }
                if (err !== closedException) {
                    errorConsumer(err)
                }

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

    private class ChannelRequestSubscriber(private val next: (Frame) -> Unit,
                                           private val error: (Throwable) -> Unit,
                                           private val complete: (Boolean) -> Unit)
        : Subscriber<Frame> {
        private var empty = true

        override fun onComplete() {
            complete(empty)
        }

        override fun onSubscribe(s: Subscription) {
            s.request(Long.MAX_VALUE)
        }

        override fun onNext(frame: Frame) {
            empty = false
            next(frame)
        }

        override fun onError(err: Throwable) {
            error(err)
        }
    }

    companion object {
        private val closedException = ClosedChannelException()
    }
}
