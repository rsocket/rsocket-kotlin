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
import io.reactivex.processors.AsyncProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.exceptions.ConnectionException
import io.rsocket.exceptions.Exceptions
import io.rsocket.internal.LimitedRequestPublisher
import io.rsocket.util.ExceptionUtil.noStacktrace
import io.rsocket.util.PayloadImpl
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Client Side of a RSocket socket. Sends [Frame]s to a [RSocketServer]  */
internal class RSocketClient @JvmOverloads constructor(
        private val connection: DuplexConnection,
        private val errorConsumer: (Throwable) -> Unit,
        private val streamIdSupplier: StreamIdSupplier,
        tickPeriod: Duration = Duration.ZERO,
        ackTimeout: Duration = Duration.ZERO,
        missedAcks: Int = 0) : RSocket {
    private val started: AsyncProcessor<Void> = AsyncProcessor.create()
    private val completeOnStart = started.ignoreElements()

    private val senders: IntObjectHashMap<LimitedRequestPublisher<*>> = IntObjectHashMap(256, 0.9f)
    private val receivers: IntObjectHashMap<Subscriber<Payload>> = IntObjectHashMap(256, 0.9f)
    private val missedAckCounter: AtomicInteger = AtomicInteger()
    @Volatile private var sendError = false
    private val sendProcessor: PublishProcessor<Frame> = PublishProcessor.create()

    private var keepAliveSendSub: Disposable? = null
    @Volatile private var timeLastTickSentMs: Long = 0

    init {

        // DO NOT Change the order here. The Send processor must be subscribed to before receiving connections

        if (Duration.ZERO != tickPeriod) {
            val ackTimeoutMs = ackTimeout.toMillis

            this.keepAliveSendSub = completeOnStart
                    .andThen (Flowable.interval(tickPeriod.value,tickPeriod.unit))
                    .doOnSubscribe { s -> timeLastTickSentMs = System.currentTimeMillis() }
                    .flatMapCompletable { i -> sendKeepAlive(ackTimeoutMs, missedAcks) }
                    .doOnError { t ->
                        errorConsumer(t)
                        connection.close().subscribe()
                    }
                    .subscribe()
        }

        connection.onClose().doFinally { cleanup() }.doOnError(errorConsumer).subscribe()

        connection
                .send(sendProcessor)
                .doOnError { handleSendProcessorError(it) }
                .doFinally { handleSendProcessorCancel() }
                .subscribe()

        connection
                .receive()
                .doOnSubscribe { subscription -> started.onComplete() }
                .doOnNext { this.handleIncomingFrames(it) }
                .doOnError(errorConsumer)
                .subscribe()
    }

    private fun handleSendProcessorError(t: Throwable) {
        sendError = true
        val (values, values1) = synchronized(this@RSocketClient) {
            Pair(receivers.values, senders.values)
        }

        for (subscriber in values) {
            try {
                subscriber.onError(t)
            } catch (e: Throwable) {
                errorConsumer(e)
            }

        }

        for (p in values1) {
            p.cancel()
        }
    }

    private fun handleSendProcessorCancel() {
        if (sendError) return

        val (values, values1) = synchronized(this@RSocketClient) {
            Pair(receivers.values, senders.values)
        }

        for (subscriber in values) {
            try {
                subscriber.onError(Throwable("closed connection"))
            } catch (e: Throwable) {
                errorConsumer(e)
            }
        }

        for (p in values1) {
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
            val requestFrame = Frame.Request.from(streamId, FrameType.FIRE_AND_FORGET, payload, 1)
            sendProcessor.onNext(requestFrame)
        }

        return completeOnStart.andThen(defer)
    }

    override fun requestResponse(payload: Payload): Single<Payload> {
        return handleRequestResponse(payload)
    }

    override fun requestStream(payload: Payload): Flowable<Payload> {
        return handleRequestStream(payload)
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return Flowable.error(UnsupportedOperationException("not implemented"))
    }

    override fun metadataPush(payload: Payload): Completable {
        val requestFrame = Frame.Request.from(0, FrameType.METADATA_PUSH, payload, 1)
        sendProcessor.onNext(requestFrame)
        return Completable.complete()
    }

    override fun availability(): Double {
        return connection.availability()
    }

    override fun close(): Completable {
        return connection.close()
    }

    override fun onClose(): Completable {
        return connection.onClose()
    }

    fun handleRequestStream(payload: Payload): Flowable<Payload> {
        return completeOnStart.andThen(
                Flowable.defer {
                    val streamId = streamIdSupplier.nextStreamId()

                    val receiver = UnicastProcessor.create<Payload>()

                    synchronized(this) {
                        receivers.put(streamId, receiver)
                    }

                    val first = AtomicBoolean(false)

                    receiver
                            .doOnRequest { l ->
                                if (first.compareAndSet(false, true)) {
                                    val requestFrame = Frame.Request.from(streamId, FrameType.REQUEST_STREAM, payload, l)

                                    sendProcessor.onNext(requestFrame)
                                } else if (contains(streamId)
                                        && connection.availability() > 0.0) {
                                    sendProcessor.onNext(Frame.RequestN.from(streamId, l))
                                }
                            }
                            .doOnError { t ->
                                if (contains(streamId)
                                        && connection.availability() > 0.0) {
                                    sendProcessor.onNext(Frame.Error.from(streamId, t))
                                }
                            }
                            .doOnCancel {
                                if (contains(streamId)
                                        && connection.availability() > 0.0) {
                                    sendProcessor.onNext(Frame.Cancel.from(streamId))
                                }
                            }
                            .doFinally { removeReceiver(streamId) }
                })
    }

    private fun handleRequestResponse(payload: Payload): Single<Payload> {
        return completeOnStart.andThen(
                Single.defer {
                    val streamId = streamIdSupplier.nextStreamId()
                    val requestFrame = Frame.Request.from(streamId, FrameType.REQUEST_RESPONSE, payload, 1)

                    val receiver = AsyncProcessor.create<Payload>()

                    synchronized(this) {
                        receivers.put(streamId, receiver)
                    }

                    sendProcessor.onNext(requestFrame)

                    receiver
                            .doOnError { t -> sendProcessor.onNext(Frame.Error.from(streamId, t)) }
                            .doOnCancel { sendProcessor.onNext(Frame.Cancel.from(streamId)) }
                            .doFinally { removeReceiver(streamId) }
                            .firstOrError()
                })
    }

    private operator fun contains(streamId: Int): Boolean {
        synchronized(this@RSocketClient) {
            return receivers.containsKey(streamId)
        }
    }

    protected fun cleanup() {
        senders.forEach { integer, limitableRequestPublisher -> cleanUpLimitableRequestPublisher(limitableRequestPublisher) }

        receivers.forEach { integer, subscriber -> cleanUpSubscriber(subscriber) }

        synchronized(this) {
            senders.clear()
            receivers.clear()
        }

        if (null != keepAliveSendSub) {
            keepAliveSendSub!!.dispose()
        }
    }

    @Synchronized private fun cleanUpLimitableRequestPublisher(
            limitableRequestPublisher: LimitedRequestPublisher<*>) {
        limitableRequestPublisher.cancel()
    }

    @Synchronized private fun cleanUpSubscriber(subscriber: Subscriber<*>) {
        subscriber.onError(CLOSED_CHANNEL_EXCEPTION)
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

        var receiver = synchronized(this) {
            receivers.get(streamId)
        }
        if (receiver == null) {
            handleMissingResponseProcessor(streamId, type, frame)
        } else {
            when (type) {
                FrameType.ERROR -> {
                    receiver.onError(Exceptions.from(frame))
                    removeReceiver(streamId)
                }
                FrameType.NEXT_COMPLETE -> {
                    receiver.onNext(PayloadImpl(frame))
                    receiver.onComplete()
                }
                FrameType.CANCEL -> {

                    var sender = synchronized(this) {
                        val s = senders.remove(streamId)
                        removeReceiver(streamId)
                        s
                    }
                    if (sender != null) {
                        sender.cancel()
                    }
                }
                FrameType.NEXT -> receiver.onNext(PayloadImpl(frame))
                FrameType.REQUEST_N -> {
                    var sender = synchronized(this) {
                        senders.get(streamId)
                    }
                    if (sender != null) {
                        val n = Frame.RequestN.requestN(frame)
                        sender.increaseRequestLimit(n.toLong())
                    }
                }
                FrameType.COMPLETE -> {
                    receiver.onComplete()
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
}
