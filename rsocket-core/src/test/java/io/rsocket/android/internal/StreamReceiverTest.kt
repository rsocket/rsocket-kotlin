package io.rsocket.android.internal

import io.reactivex.Flowable
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.android.Payload
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamReceiverTest {
    lateinit var receiver: StreamReceiver
    lateinit var subs: TestSubscriber<Payload>
    @Before
    fun setUp() {
        receiver = StreamReceiver.create()
        subs = TestSubscriber.create<Payload>(0)
    }

    @Test
    fun requestAfterError() {
        request { receiver.onError(RuntimeException()) }
    }

    @Test
    fun requestAfterComplete() {
        request { receiver.onComplete() }
    }

    @Test
    fun requestAfterCancel() {
        request { subs.cancel() }
    }

    fun request(f: () -> Unit) {
        val expectedEqs = arrayListOf(100L, 200L, 300L, 400L)
        val actualReqs = ArrayList<Long>()
        val untilIndex = 1
        val until = expectedEqs[untilIndex]

        receiver.doOnRequestIfActive { actualReqs += it }.subscribe(subs)
        val ticks = Flowable.fromIterable(expectedEqs)
                .flatMap { Flowable.timer(it, TimeUnit.MILLISECONDS).map { _ -> it } }
                .share()

        ticks.take(expectedEqs.size.toLong() - 1)
                .doOnNext {
                    if (it == until)
                        f()
                    else
                        subs.request(it)
                }.subscribe()

        ticks.ignoreElements().blockingAwait()
        assertEquals(untilIndex, actualReqs.size)
        for (i in 0 until untilIndex) {
            assertEquals(expectedEqs[i], actualReqs[i])
        }
    }
}