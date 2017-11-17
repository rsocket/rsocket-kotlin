/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package io.rsocket.fragmentation

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.DuplexConnection
import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.util.PayloadImpl
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.reactivestreams.Publisher
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

class FragmentationDuplexConnectionTest {

    class MockConnection : DuplexConnection {
        private val sent = PublishProcessor.create<Frame>()

        override fun send(frame: Publisher<Frame>): Completable =
                Flowable.fromPublisher(frame)
                        .doOnNext { n -> sent.onNext(n) }
                        .doOnComplete { sent.onComplete() }
                        .ignoreElements()

        override fun receive(): Flowable<Frame> = Flowable.empty()

        override fun availability(): Double = 1.0

        override fun close(): Completable = Completable.complete()

        override fun onClose(): Completable = Completable.complete()

        fun sentFrames(): Flowable<Frame> = sent
    }

    @Test
    fun testSendOneWithFragmentation() {

        val mockConnection = MockConnection()

        val sentSubscriber = TestSubscriber.create<Frame>()
        mockConnection.sentFrames().subscribe(sentSubscriber)

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }.subscribeOn(Schedulers.io()).blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        Assert.assertEquals(16, sentSubscriber.valueCount())
        Completable.complete()
  }

    private fun <T> any(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    @Test
    fun testShouldNotFragment() {
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.sendOne(any())).thenReturn(Completable.complete())

        val frame = Frame.Cancel.from(1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)
        val subs = TestSubscriber.create<Void>()
        duplexConnection.sendOne(frame).toFlowable<Void>().blockingSubscribe(subs)
        subs.assertComplete()

        verify(mockConnection, times(1)).sendOne(frame)
    }

    @Test
    fun testShouldFragmentMultiple() {
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.send(any()))
                .then { invocation ->
                    val subs = TestSubscriber.create<Frame>()
                    val frames = Flowable.fromPublisher(invocation.getArgument<Publisher<Frame>>(0))
                    frames.blockingSubscribe(subs)
                    subs.assertNoErrors()
                    subs.assertComplete()
                    Assert.assertEquals(16, subs.valueCount())
                    Completable.complete()
                }
        `when`(mockConnection.sendOne(any())).thenReturn(Completable.complete())

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame1 = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frame2 = Frame.Request.from(
                2, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frame3 = Frame.Request.from(
                3, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        val subs = TestSubscriber.create<Void>()
        duplexConnection.send(Flowable.just(frame1, frame2, frame3)).toFlowable<Void>().blockingSubscribe(subs)

        subs.assertNoErrors()
        subs.assertComplete()

        verify(mockConnection, times(3)).send(any())
    }

    @Test
    fun testReassembleFragmentFrame() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)
        val frame = Frame.Request.from(
                1024, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frameFragmenter = FrameFragmenter(2)
        val fragmentedFrames = frameFragmenter.fragment(frame)
        val processor = PublishProcessor.create<Frame>()
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.receive()).then { processor }

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        fragmentedFrames.subscribe(processor)

        duplexConnection
                .receive()
                .doOnNext { c -> println("here - " + c.toString()) }
                .blockingSubscribe()
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
}