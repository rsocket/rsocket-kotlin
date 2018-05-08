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
import io.reactivex.FlowableSubscriber
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.exceptions.ApplicationException
import io.rsocket.android.exceptions.ChannelRequestException
import io.rsocket.android.exceptions.ConnectionException
import io.rsocket.android.exceptions.Exceptions
import io.rsocket.android.internal.RequestingPublisher
import io.rsocket.android.internal.StreamReceiver
import io.rsocket.android.util.ExceptionUtil.noStacktrace
import io.rsocket.android.util.PayloadImpl
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Client Side of a RSocket socket. Sends [Frame]s to a [RSocketServer]  */
internal class RSocketClient @JvmOverloads constructor(
        private val connection: DuplexConnection,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamIdSupplier: StreamIdSupplier,
        private val streamDemandLimit: Int,
        tickPeriod: Duration = Duration.ZERO,
        ackTimeout: Duration = Duration.ZERO,
        missedAcks: Int = 0) : RSocket {

    private val senders = ConcurrentHashMap<Int, Subscription>(256)
    private val receivers = ConcurrentHashMap<Int, Subscriber<Payload>>(256)
    private val missedAckCounter: AtomicInteger = AtomicInteger()
    private val interactions = Interactions()

    private val sentFrames: FlowableProcessor<Frame> = UnicastProcessor
            .create<Frame>()
            .toSerialized()

    private var keepAliveSendSub: Disposable? = null
    @Volatile
    private var timeLastTickSentMs: Long = 0

    init {
        connection
                .send(sentFrames)
                .subscribe(
                        {},
                        { interactions.error(it) })
        connection
                .receive()
                .subscribe(
                        { handleFrame(it) },
                        { interactions.error(it) })
        connection
                .onClose()
                .subscribe(
                        { interactions.complete() },
                        errorConsumer)

        if (Duration.ZERO != tickPeriod) {
            val ackTimeoutMs = ackTimeout.toMillis
            this.keepAliveSendSub =
                    Flowable.interval(tickPeriod.toMillis, TimeUnit.MILLISECONDS)
                            .doOnSubscribe { _ -> timeLastTickSentMs = System.currentTimeMillis() }
                            .concatMap { _ -> sendKeepAlive(ackTimeoutMs, missedAcks).toFlowable<Long>() }
                            .subscribe({},
                                    { t: Throwable ->
                                        errorConsumer(t)
                                        connection.close().subscribe({}, errorConsumer)
                                    })
        }
    }

    override fun fireAndForget(payload: Payload) =
            interactions.fireAndForget(handleFireAndForget(payload))

    override fun requestResponse(payload: Payload) =
            interactions.requestResponse(handleRequestResponse(payload))

    override fun requestStream(payload: Payload): Flowable<Payload> =
            interactions.requestStream(
                    handleRequestStream(payload).rebatchRequests(streamDemandLimit))

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
            interactions.requestChannel(
                    handleChannel(
                            Flowable.fromPublisher(payloads)
                                    .rebatchRequests(streamDemandLimit)
                    ).rebatchRequests(streamDemandLimit))

    override fun metadataPush(payload: Payload): Completable =
            interactions.metadataPush(doMetadataPush(payload))

    override fun availability(): Double = connection.availability()

    override fun close(): Completable = connection.close()

    override fun onClose(): Completable = connection.onClose()

    private fun handleFireAndForget(payload: Payload): Completable {
        return Completable.fromRunnable {
            val streamId = streamIdSupplier.nextStreamId()
            val requestFrame = Frame.Request.from(
                    streamId,
                    FrameType.FIRE_AND_FORGET,
                    payload,
                    1)
            sentFrames.onNext(requestFrame)
        }
    }

    private fun handleRequestResponse(payload: Payload): Single<Payload> {
        return Single.defer {
            val streamId = streamIdSupplier.nextStreamId()
            val requestFrame = Frame.Request.from(
                    streamId, FrameType.REQUEST_RESPONSE, payload, 1)

            val receiver = PublishProcessor.create<Payload>()
            receivers[streamId] = receiver
            sentFrames.onNext(requestFrame)

            receiver
                    .doOnCancel { sentFrames.onNext(Frame.Cancel.from(streamId)) }
                    .doFinally { receivers -= streamId }
                    .firstOrError()
        }
    }

    private fun handleRequestStream(payload: Payload): Flowable<Payload> {
        return Flowable.defer {
            val streamId = streamIdSupplier.nextStreamId()
            val receiver = StreamReceiver.create()
            receivers[streamId] = receiver
            val doOnRequestN = StartThen<Frame>()

            receiver.doOnRequestIfActive { requestN ->

                val frame = doOnRequestN({
                    Frame.Request.from(
                            streamId,
                            FrameType.REQUEST_STREAM,
                            payload,
                            requestN)
                } then {
                    Frame.RequestN.from(
                            streamId,
                            requestN)
                })
                sentFrames.onNext(frame)
            }.doOnCancel {
                sentFrames.onNext(Frame.Cancel.from(streamId))
            }.doFinally {
                receivers -= streamId
            }
        }
    }

    private fun handleChannel(request: Flowable<Payload>): Flowable<Payload> {
        return Flowable.defer {
            val receiver = StreamReceiver.create()
            val streamId = streamIdSupplier.nextStreamId()
            val doOnRequestN = StartThen<Unit>()

            receiver.doOnRequestIfActive { requestN ->

                doOnRequestN({
                    val wrappedRequest = request.compose {
                        val sender = RequestingPublisher.wrap(it)
                        sender.request(1)
                        senders[streamId] = sender
                        receivers[streamId] = receiver
                        sender
                    }.publish().autoConnect(2)

                    val first = wrappedRequest.take(1)
                            .map { payload ->
                                Frame.Request.from(
                                        streamId,
                                        FrameType.REQUEST_CHANNEL,
                                        payload,
                                        requestN)
                            }
                    val rest = wrappedRequest.skip(1)
                            .map { payload ->
                                Frame.PayloadFrame.from(
                                        streamId,
                                        FrameType.NEXT,
                                        payload)
                            }
                    val requestFrames = first.mergeWith(rest)
                    requestFrames.subscribe(
                            ChannelRequestSubscriber(
                                    { payload -> sentFrames.onNext(payload) },
                                    {
                                        receiver.onError(ChannelRequestException(
                                                "Channel request exception", it))
                                    },
                                    { empty ->
                                        if (empty) {
                                            receiver.onComplete()
                                        } else {
                                            sentFrames.onNext(Frame.PayloadFrame.from(
                                                    streamId, FrameType.COMPLETE))
                                        }
                                    }))

                } then {
                    sentFrames.onNext(Frame.RequestN.from(streamId, requestN))
                })
            }.doOnError { err ->
                if (err is ChannelRequestException) {
                    sentFrames.onNext(Frame.Error.from(streamId,
                            ApplicationException(err.message, err.cause)))
                }
            }.doOnCancel {
                sentFrames.onNext(Frame.Cancel.from(streamId))
            }.doFinally {
                receivers -= streamId
                senders.remove(streamId)?.cancel()
            }
        }
    }

    private fun doMetadataPush(payload: Payload): Completable {
        return Completable.fromRunnable {
            val requestFrame = Frame.Request.from(
                    0,
                    FrameType.METADATA_PUSH,
                    payload,
                    1)
            sentFrames.onNext(requestFrame)
        }
    }

    private fun sendKeepAlive(ackTimeoutMs: Long, missedAcks: Int): Completable {
        return Completable.fromRunnable {
            val now = System.currentTimeMillis()
            if (now - timeLastTickSentMs > ackTimeoutMs) {
                val count = missedAckCounter.incrementAndGet()
                if (count >= missedAcks) {
                    val message = String.format(
                            "Missed %d keep-alive acks with a threshold of %d and a ack timeout of %d ms",
                            count, missedAcks, ackTimeoutMs)
                    throw ConnectionException(message)
                }
            }
            sentFrames.onNext(
                    Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        }
    }

    private fun handleFrame(frame: Frame) {
        try {
            val streamId = frame.streamId
            val type = frame.type
            when (streamId) {
                0 -> handleConnectionFrame(type, frame)
                else -> handleStreamFrame(streamId, type, frame)
            }
        } finally {
            frame.release()
        }
    }

    private fun handleConnectionFrame(type: FrameType, frame: Frame) {
        when (type) {
            FrameType.ERROR -> throw Exceptions.from(frame)
            FrameType.LEASE -> {
            }
            FrameType.KEEPALIVE -> if (!Frame.Keepalive.hasRespondFlag(frame)) {
                timeLastTickSentMs = System.currentTimeMillis()
            }
            else -> unsupportedConnectionFrame(frame)
        }
    }

    private fun handleStreamFrame(streamId: Int, type: FrameType, frame: Frame) {
        receivers[streamId]?.let { receiver ->
            when (type) {
                FrameType.ERROR -> {
                    receiver.onError(Exceptions.from(frame))
                }
                FrameType.NEXT_COMPLETE -> {
                    receiver.onNext(PayloadImpl(frame))
                    receiver.onComplete()
                }
                FrameType.CANCEL -> {
                    val sender = senders.remove(streamId)
                    sender?.cancel()
                    receivers -= streamId
                }
                FrameType.NEXT -> receiver.onNext(PayloadImpl(frame))
                FrameType.REQUEST_N -> {
                    val sender = senders[streamId]
                    sender?.let {
                        val n = Frame.RequestN.requestN(frame).toLong()
                        it.request(n)
                    }
                }
                FrameType.COMPLETE -> {
                    receiver.onComplete()
                }
                else -> unsupportedFrame(streamId, frame)
            }
        } ?: missingReceiver(streamId, type, frame)
    }

    private fun unsupportedConnectionFrame(frame: Frame) =
            errorConsumer(IllegalStateException(
                    "Client received supported frame on stream 0: $frame"))


    private fun unsupportedFrame(streamId: Int, frame: Frame) {
        errorConsumer(IllegalStateException(
                "Client received unsupported frame on stream " +
                        "$streamId : $frame"))
    }

    private fun missingReceiver(streamId: Int, type: FrameType, frame: Frame) {
        if (!streamIdSupplier.isBeforeOrCurrent(streamId)) {
            val err = if (type === FrameType.ERROR) {
                IllegalStateException(
                        "Client received error for non-existent stream: " +
                                "$streamId Message: ${frame.dataUtf8}")
            } else {
                IllegalStateException(
                        "Client received message for non-existent stream: " +
                                "$streamId, frame type: $type")
            }
            errorConsumer(err)
        }
    }

    private inner class Interactions {
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
                keepAliveSendSub?.dispose()
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

    private class StartThen<T> {
        private var init = true

        operator fun invoke(calls: Calls<T>): T =
                if (init) {
                    init = false
                    calls.start()
                } else {
                    calls.end()
                }

        data class Calls<T>(inline val start: () -> T, inline val end: () -> T)

    }

    private infix fun <T> (() -> T).then(end: () -> T)
            : StartThen.Calls<T> = StartThen.Calls(this, end)

    private class ChannelRequestSubscriber(private val next: (Frame) -> Unit,
                                           private val error: (Throwable) -> Unit,
                                           private val complete: (Boolean) -> Unit)
        : FlowableSubscriber<Frame> {
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
        private val closedException = noStacktrace(ClosedChannelException())
    }
}
