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

package io.rsocket.kotlin.internal.fragmentation

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_F
import io.rsocket.kotlin.DefaultPayload
import org.junit.Assert.*
import org.junit.Before
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

    lateinit var mockConnection: MockConnection
    lateinit var sentSubscriber :TestSubscriber<Frame>

    @Before
    fun setUp() {
        mockConnection = MockConnection()

        sentSubscriber = TestSubscriber.create<Frame>()
        mockConnection.sentFrames().subscribe(sentSubscriber)
    }
    @Test
    fun dataMetadataAboveMtu() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

        val mtu = 2
        val duplexConnection = FragmentationDuplexConnection(mockConnection, mtu)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        assertEquals(16, sentSubscriber.valueCount())
        val frames = sentSubscriber.values()
        val lastFrame = frames.last()
        val firstFrames = frames.take(15)
        val metadataFrames = frames.take(8)
        val dataFrames = frames.takeLast(8)
        firstFrames.forEach {
            assertTrue(it.isFlagSet(FLAGS_F))
        }
        assertFalse(lastFrame.isFlagSet(FLAGS_F))
        metadataFrames.forEach {
            assertTrue(it.metadata.remaining() == mtu)
            assertFalse(it.data.hasRemaining())
        }

        dataFrames.forEach {
            assertFalse(it.metadata.hasRemaining())
            assertTrue(it.data.remaining() == mtu)
        }
    }

    @Test
    fun dataAboveMtu() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(1)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

        val mtu = 2
        val duplexConnection = FragmentationDuplexConnection(mockConnection, mtu)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        assertEquals(9, sentSubscriber.valueCount())
        val frames = sentSubscriber.values()
        val lastFrame = frames.last()
        val firstFrames = frames.take(8)
        val firstFrame = frames.first()
        firstFrames.forEach {
            assertTrue(it.isFlagSet(FLAGS_F))
        }
        assertFalse(lastFrame.isFlagSet(FLAGS_F))
        assertTrue(firstFrame.metadata.remaining() == 1)
        assertTrue(firstFrame.data.remaining() == 1)
        assertTrue(lastFrame.metadata.remaining() == 0)
        assertTrue(lastFrame.data.remaining() == 1)
    }

    @Test
    fun dataAboveMtuNullMetadata() {
        val data = createRandomBytes(16)
        val metadata = null

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

        val mtu = 2
        val duplexConnection = FragmentationDuplexConnection(mockConnection, mtu)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        assertEquals(8, sentSubscriber.valueCount())
        val frames = sentSubscriber.values()
        val lastFrame = frames.last()
        val firstFrames = frames.take(7)
        firstFrames.forEach {
            assertTrue(it.isFlagSet(FLAGS_F))
        }
        assertFalse(lastFrame.isFlagSet(FLAGS_F))
    }

    @Test
    fun dataMetadataBelowMtu() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(1)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

        val mtu = 20
        val duplexConnection = FragmentationDuplexConnection(mockConnection, mtu)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        assertEquals(1, sentSubscriber.valueCount())
        val firstFrame = sentSubscriber.values().first()
        assertFalse(firstFrame.isFlagSet(FLAGS_F))
    }

    @Test
    fun zeroMtu() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(1)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

        val mtu = 0
        val duplexConnection = FragmentationDuplexConnection(mockConnection, mtu)
        val subs = TestSubscriber.create<Frame>()
        Flowable.defer { duplexConnection.sendOne(frame).toFlowable<Frame>() }
                .subscribeOn(Schedulers.io())
                .blockingSubscribe(subs)
        subs.assertComplete()

        sentSubscriber.assertNoErrors()
        sentSubscriber.assertComplete()
        assertEquals(1, sentSubscriber.valueCount())
        val firstFrame = sentSubscriber.values().first()
        assertFalse(firstFrame.isFlagSet(FLAGS_F))
    }

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
                    assertEquals(16, subs.valueCount())
                    Completable.complete()
                }
        `when`(mockConnection.sendOne(any())).thenReturn(Completable.complete())

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame1 = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)
        val frame2 = Frame.Request.from(
                2, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)
        val frame3 = Frame.Request.from(
                3, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)

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
                1024, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)
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

    private fun <T> any(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}