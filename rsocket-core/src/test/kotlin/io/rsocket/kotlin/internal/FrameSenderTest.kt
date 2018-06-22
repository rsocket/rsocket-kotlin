package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.TimeUnit

class FrameSenderTest {
    private lateinit var frameSender: FrameSender
    @Before
    fun setUp() {
        frameSender = FrameSender()
    }

    @Test
    fun unsubscribeBeforeSend() {
        val frames = mockFrames(12)
        val subs = TestSubscriber(1)
        frameSender.sent().subscribe(subs)
        frames.forEach {
            frameSender.send(it)
        }
        subs.subscription().blockingGet().cancel()
        frames.forEach {
            assertTrue(it.refCnt() == 0)
        }
    }

    @Test
    fun sendAfterUnsubscribe() {
        val subs = TestSubscriber(1)
        frameSender.sent().subscribe(subs)
        subs.subscription().blockingGet().cancel()
        val frame = mockFrame()
        frameSender.send(frame)
        assertTrue(frame.refCnt() == 0)
    }

    @Test
    fun send() {
        val frame = mockFrame()
        frameSender.send(frame)
        val sent = frameSender.sent()
                .takeUntil(Flowable.timer(100,
                        TimeUnit.MILLISECONDS)).toList().blockingGet()
        assertEquals(1, sent.size)
        val actual = sent[0]
        assertEquals(frame.type, actual.type)
        assertEquals(frame.streamId, actual.streamId)
        assertEquals(frame.dataUtf8, actual.dataUtf8)
    }

    private fun mockFrames(n: Int): List<Frame> =
            Flowable.range(0, n).map { mockFrame() }
                    .toList()
                    .blockingGet()

    private fun mockFrame() = Frame.Request.from(1,
            FrameType.REQUEST_RESPONSE, DefaultPayload.EMPTY, 42)

    private class TestSubscriber(private val requestN: Long) : Subscriber<Frame> {
        private val s = UnicastProcessor.create<Subscription>()
        override fun onComplete() {
        }

        override fun onSubscribe(s: Subscription) {
            this.s.onNext(s)
            s.request(requestN)
        }

        override fun onNext(t: Frame) {
            t.release()
        }

        override fun onError(t: Throwable) {
        }

        fun subscription(): Single<Subscription> = s.firstOrError()
    }
}