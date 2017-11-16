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

import io.rsocket.util.ExceptionUtil.noStacktrace

import io.netty.buffer.Unpooled
import io.netty.util.collection.IntObjectHashMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.processors.AsyncProcessor
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.exceptions.ConnectionException
import io.rsocket.exceptions.Exceptions
import io.rsocket.internal.LimitableRequestPublisher
import io.rsocket.util.PayloadImpl

import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Client Side of a RSocket socket. Sends [Frame]s to a [RSocketServer]  */
internal open class RSocketClient @JvmOverloads constructor(
        private val connection: DuplexConnection,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamIdSupplier: StreamIdSupplier,
        tickPeriod: Duration = Duration.ZERO,
        ackTimeout: Duration = Duration.ZERO,
        missedAcks: Int = 0) : RSocket {
    private val started: AsyncProcessor<Void> = AsyncProcessor.create()
    private val completeOnStart = started.ignoreElements()
    private val senders: IntObjectHashMap<LimitableRequestPublisher<*>> = IntObjectHashMap(256, 0.9f)
    private val receivers: IntObjectHashMap<Subscriber<Payload>> = IntObjectHashMap(256, 0.9f)
    private val missedAckCounter: AtomicInteger = AtomicInteger()

    private val sendProcessor: FlowableProcessor<Frame> = PublishProcessor
            .create<Frame>()
            .toSerialized()

    private var keepAliveSendSub: Disposable? = null
    @Volatile private var timeLastTickSentMs: Long = 0

    init {
        // DO NOT Change the order here. The Send processor must be subscribed to before receiving
        if (Duration.ZERO != tickPeriod) {
            val ackTimeoutMs = ackTimeout.toMillis

            this.keepAliveSendSub = started
                    .flatMap{ _ -> Flowable.interval(tickPeriod.toMillis,TimeUnit.SECONDS)}
                    .doOnSubscribe({ _ -> timeLastTickSentMs = System.currentTimeMillis() })
                    .concatMap({ _ -> sendKeepAlive(ackTimeoutMs, missedAcks).toFlowable<Long>() })
                    .doOnError({ t:Throwable ->
                                errorConsumer(t)
                                connection.close().subscribe()
                            })
                    .subscribe()
        }

        connection
                .onClose()
                .doFinally { cleanup() }
                .doOnError(errorConsumer)
                .subscribe()

        connection
                .send(sendProcessor)
                .doOnError { handleSendProcessorError(it) }
                .subscribe()

        connection
                .receive()
                .doOnSubscribe { started.onComplete() }
                .doOnNext { handleIncomingFrames(it) }
                .doOnError(errorConsumer)
                .subscribe()
    }

    private fun handleSendProcessorError(t: Throwable) {
        val (receivers, senders) = synchronized(this) {
            Pair(receivers.values, senders.values)
        }
        for (subscriber in receivers) {
            try {
                subscriber.onError(t)
            } catch (e: Throwable) {
                errorConsumer(e)
            }
        }

        for (p in senders) {
            p.cancel()
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

                    sendProcessor.onNext(Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
                }
    }

    override fun fireAndForget(payload: Payload): Completable {
        val defer = Completable.fromRunnable {
                    val streamId = streamIdSupplier.nextStreamId()
                    val requestFrame = Frame.Request.from(
                            streamId,
                            FrameType.FIRE_AND_FORGET,
                            payload,
                            1)
                    sendProcessor.onNext(requestFrame)
                }

        return completeOnStart.andThen(defer)
    }

    override fun requestResponse(payload: Payload): Single<Payload> =
            handleRequestResponse(payload)

    override fun requestStream(payload: Payload): Flowable<Payload> =
            handleRequestStream(payload)

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
            handleChannel(Flowable.fromPublisher(payloads), FrameType.REQUEST_CHANNEL)

    override fun metadataPush(payload: Payload): Completable {
        val requestFrame = Frame.Request.from(
                0,
                FrameType.METADATA_PUSH,
                payload,
                1)
        sendProcessor.onNext(requestFrame)
        return Completable.complete()
    }

    override fun availability(): Double = connection.availability()

    override fun close(): Completable = connection.close()

    override fun onClose(): Completable = connection.onClose()

    private fun handleRequestStream(payload: Payload): Flowable<Payload> {
        return completeOnStart.andThen(
                Flowable.defer {
                            val streamId = streamIdSupplier.nextStreamId()
                            val receiver = UnicastProcessor.create<Payload>()
                            synchronized(this) {
                                receivers.put(streamId, receiver)
                            }

                            val first = AtomicBoolean(false)

                            receiver
                                    .doOnRequest{ l ->
                                                if (first.compareAndSet(false, true) && !receiver.isTerminated()) {
                                                    val requestFrame = Frame.Request.from(streamId, FrameType.REQUEST_STREAM, payload, l)
                                                    sendProcessor.onNext(requestFrame)
                                                } else if (contains(streamId)) {
                                                    sendProcessor.onNext(Frame.RequestN.from(streamId, l))
                                                }
                                            }
                                    .doOnError { t ->
                                                if (contains(streamId) && !receiver.isTerminated()) {
                                                    sendProcessor.onNext(Frame.Error.from(streamId, t))
                                                }
                                            }
                                    .doOnCancel {
                                                if (contains(streamId) && !receiver.isTerminated()) {
                                                    sendProcessor.onNext(Frame.Cancel.from(streamId))
                                                }
                                            }
                                    .doFinally { removeReceiver(streamId) }
                        })
    }

    private fun handleRequestResponse(payload: Payload): Single<Payload> {
        return completeOnStart.andThen(
                Single.defer(
                        {
                            val streamId = streamIdSupplier.nextStreamId()
                            val requestFrame = Frame.Request.from(streamId, FrameType.REQUEST_RESPONSE, payload, 1)

                            val receiver = PublishProcessor.create<Payload>()

                            synchronized(this) {
                                receivers.put(streamId, receiver)
                            }

                            sendProcessor.onNext(requestFrame)

                            receiver
                                    .doOnError{ t -> sendProcessor.onNext(Frame.Error.from(streamId, t)) }
                                    .doOnCancel{ sendProcessor.onNext(Frame.Cancel.from(streamId)) }
                                    .doFinally { removeReceiver(streamId) }
                                    .firstOrError()
                        }))
    }

    private fun handleChannel(request: Flowable<Payload>, requestType: FrameType): Flowable<Payload> {
        return completeOnStart.andThen(
                Flowable.defer(
                        object : () -> Flowable<Payload> {
                            internal val receiver = UnicastProcessor.create<Payload>()
                            internal val streamId = streamIdSupplier.nextStreamId()
                            internal var firstRequest = true

                            internal val isValidToSendFrame: Boolean
                                get() = contains(streamId) && !receiver.isTerminated()

                            internal fun sendOneFrame(frame: Frame) {
                                if (isValidToSendFrame) {
                                    sendProcessor.onNext(frame)
                                }
                            }

                            override fun invoke(): Flowable<Payload> {
                                return receiver
                                        .doOnRequest(
                                                { l ->
                                                    var _firstRequest = false
                                                    synchronized(this) {
                                                        if (firstRequest) {
                                                            _firstRequest = true
                                                            firstRequest = false
                                                        }
                                                    }

                                                    if (_firstRequest) {
                                                        val firstPayload = AtomicBoolean(true)
                                                        val requestFrames = request
                                                                .compose { f ->
                                                                    val wrapped = LimitableRequestPublisher.wrap(f)
                                                                    // Need to set this to one for first the frame
                                                                    wrapped.increaseRequestLimit(1)
                                                                    synchronized(this) {
                                                                        senders.put(streamId, wrapped)
                                                                        receivers.put(streamId, receiver)
                                                                    }
                                                                    wrapped
                                                                }
                                                                .map { payload ->
                                                                    val requestFrame: Frame =
                                                                            if (firstPayload.compareAndSet(true, false)) {
                                                                                Frame.Request.from(
                                                                                        streamId, requestType, payload, l)
                                                                            } else {
                                                                                Frame.PayloadFrame.from(
                                                                                        streamId, FrameType.NEXT, payload)
                                                                            }
                                                                    requestFrame
                                                                }
                                                                .doOnComplete {
                                                                    if (FrameType.REQUEST_CHANNEL === requestType) {
                                                                        sendOneFrame(
                                                                                Frame.PayloadFrame.from(
                                                                                        streamId, FrameType.COMPLETE))
                                                                        if (firstPayload.get()) {
                                                                            receiver.onComplete()
                                                                        }
                                                                    }
                                                                }

                                                        requestFrames
                                                                .doOnNext { sendProcessor.onNext(it) }
                                                                .doOnError { t ->
                                                                    errorConsumer(t)
                                                                    receiver.onError(CancellationException("Disposed"))
                                                                }.subscribe()
                                                    } else {
                                                        sendOneFrame(Frame.RequestN.from(streamId, l))
                                                    }
                                                })
                                        .doOnError { t -> sendOneFrame(Frame.Error.from(streamId, t)) }
                                        .doOnCancel {
                                            sendOneFrame(Frame.Cancel.from(streamId))
                                        }
                                        .doFinally {
                                            removeReceiver(streamId)
                                            removeSender(streamId)
                                        }
                            }
                        }))
    }

    private operator fun contains(streamId: Int): Boolean {
        synchronized(this) {
            return receivers.containsKey(streamId)
        }
    }

    protected fun cleanup() {

        var subscribers: Collection<Subscriber<Payload>>
        var publishers: Collection<LimitableRequestPublisher<*>>
        val (subs, pubs) = synchronized(this) {

            subscribers = receivers.values
            publishers = senders.values

            senders.clear()
            receivers.clear()

            Pair(subscribers,publishers)
        }

        subs.forEach { cleanUpSubscriber(it) }
        pubs.forEach { cleanUpLimitableRequestPublisher(it) }

        keepAliveSendSub?.dispose()
    }

    @Synchronized private fun cleanUpLimitableRequestPublisher(
            limitableRequestPublisher: LimitableRequestPublisher<*>) {
        try {
            limitableRequestPublisher.cancel()
        } catch (t: Throwable) {
            errorConsumer(t)
        }

    }

    @Synchronized private fun cleanUpSubscriber(subscriber: Subscriber<*>) {
        try {
            subscriber.onError(CLOSED_CHANNEL_EXCEPTION)
        } catch (t: Throwable) {
            errorConsumer(t)
        }

    }

    private fun handleIncomingFrames(frame: Frame) {
        try {
            val streamId = frame.streamId
            val type = frame.type
            if (streamId == 0) {
                handleStreamZero(type, frame)
            } else {
                handleFrame(streamId, type, frame)
            }
        } finally {
            frame.release()
        }
    }

    private fun handleStreamZero(type: FrameType, frame: Frame) {
        when (type) {
            FrameType.ERROR -> throw Exceptions.from(frame)
            FrameType.LEASE -> {
            }
            FrameType.KEEPALIVE -> if (!Frame.Keepalive.hasRespondFlag(frame)) {
                timeLastTickSentMs = System.currentTimeMillis()
            }
            else ->
                // Ignore unknown frames. Throwing an error will close the socket.
                errorConsumer(
                        IllegalStateException(
                                "Client received supported frame on stream 0: " + frame.toString()))
        }
    }

    private fun handleFrame(streamId: Int, type: FrameType, frame: Frame) {
        var receiver: Subscriber<Payload>? = null
        synchronized(this) {
            receiver = receivers.get(streamId)
        }
        if (receiver == null) {
            handleMissingResponseProcessor(streamId, type, frame)
        } else {
            when (type) {
                FrameType.ERROR -> {
                    receiver!!.onError(Exceptions.from(frame))
                    removeReceiver(streamId)
                }
                FrameType.NEXT_COMPLETE -> {
                    receiver!!.onNext(PayloadImpl(frame))
                    receiver!!.onComplete()
                }
                FrameType.CANCEL -> {
                    var sender: LimitableRequestPublisher<*>? = null
                    synchronized(this) {
                        sender = senders.remove(streamId)
                        removeReceiver(streamId)
                    }
                    if (sender != null) {
                        sender!!.cancel()
                    }
                }
                FrameType.NEXT -> receiver!!.onNext(PayloadImpl(frame))
                FrameType.REQUEST_N -> {
                    var sender: LimitableRequestPublisher<*>? = null
                    synchronized(this) {
                        sender = senders.get(streamId)
                    }
                    if (sender != null) {
                        val n = Frame.RequestN.requestN(frame).toLong()
                        sender!!.increaseRequestLimit(n)
                    }
                }
                FrameType.COMPLETE -> {
                    receiver!!.onComplete()
                    synchronized(this) {
                        receivers.remove(streamId)
                    }
                }
                else -> throw IllegalStateException(
                        "Client received supported frame on stream " + streamId + ": " + frame.toString())
            }
        }
    }

    private fun handleMissingResponseProcessor(streamId: Int, type: FrameType, frame: Frame) {
        if (!streamIdSupplier.isBeforeOrCurrent(streamId)) {
            if (type === FrameType.ERROR) {
                // message for stream that has never existed, we have a problem with
                // the overall connection and must tear down
                val errorMessage = frame.dataUtf8

                throw IllegalStateException(
                        "Client received error for non-existent stream: "
                                + streamId
                                + " Message: "
                                + errorMessage)
            } else {
                throw IllegalStateException(
                        "Client received message for non-existent stream: "
                                + streamId
                                + ", frame type: "
                                + type)
            }
        }
        // receiving a frame after a given stream has been cancelled/completed,
        // so ignore (cancellation is async so there is a race condition)
    }

    @Synchronized private fun removeReceiver(streamId: Int) {
        receivers.remove(streamId)
    }

    @Synchronized private fun removeSender(streamId: Int) {
        senders.remove(streamId)
    }

    companion object {
        private val CLOSED_CHANNEL_EXCEPTION = noStacktrace(ClosedChannelException())
    }
    private fun <T> UnicastProcessor<T>.isTerminated(): Boolean = hasComplete() || hasThrowable()
}
