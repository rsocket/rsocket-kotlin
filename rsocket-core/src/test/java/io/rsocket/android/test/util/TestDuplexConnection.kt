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


package io.rsocket.android.test.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.rsocket.DuplexConnection
import io.rsocket.Frame
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.slf4j.LoggerFactory


/**
 * An implementation of [DuplexConnection] that provides functionality to modify the behavior
 * dynamically.
 */

class TestDuplexConnection : DuplexConnection {

    internal val sent: LinkedBlockingQueue<Frame> = LinkedBlockingQueue()
    private val sentPublisher: PublishProcessor<Frame> = PublishProcessor.create()
    private val received: PublishProcessor<Frame> = PublishProcessor.create()
    private val close: PublishProcessor<Void> = PublishProcessor.create()
    internal val sendSubscribers: ConcurrentLinkedQueue<Subscriber<Frame>> = ConcurrentLinkedQueue()
    @Volatile private var availability = 1.0
    @Volatile private var initialSendRequestN = Integer.MAX_VALUE

    val sentAsPublisher: Publisher<Frame>
        get() = sentPublisher

    override fun send(frame: Publisher<Frame>): Completable {
        if (availability <= 0) {
            return Completable.error(
                    IllegalStateException("RSocket not available. Availability: " + availability))
        }
        val subscriber = TestSubscriber.create<Frame>(initialSendRequestN.toLong())
        Flowable.fromPublisher(frame)
                .doOnNext { frame ->
                    sent.offer(frame)
                    sentPublisher.onNext(frame)
                }
                .doOnError { throwable -> logger.error("Error in send stream on test connection.", throwable) }
                .subscribe(subscriber)
        sendSubscribers.add(subscriber)
        return Completable.complete()
    }

    override fun receive(): Flowable<Frame> {
        return received
    }

    override fun availability(): Double {
        return availability
    }

    override fun close(): Completable {
        return close.ignoreElements()
    }

    override fun onClose(): Completable {
        return close()
    }

    @Throws(InterruptedException::class)
    fun awaitSend(): Frame {
        return sent.take()
    }

    fun setAvailability(availability: Double) {
        this.availability = availability
    }

    fun getSent(): Collection<Frame> {
        return sent
    }

    fun addToReceivedBuffer(vararg received: Frame) {
        for (frame in received) {
            this.received.onNext(frame)
        }
    }

    fun clearSendReceiveBuffers() {
        sent.clear()
        sendSubscribers.clear()
    }

    fun setInitialSendRequestN(initialSendRequestN: Int) {
        this.initialSendRequestN = initialSendRequestN
    }

    fun getSendSubscribers(): Collection<Subscriber<Frame>> {
        return sendSubscribers
    }

    companion object {

        private val logger = LoggerFactory.getLogger(TestDuplexConnection::class.java)
    }
}

