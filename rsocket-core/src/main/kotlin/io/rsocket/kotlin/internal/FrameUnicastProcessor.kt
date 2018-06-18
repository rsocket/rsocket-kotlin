/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.reactivex.annotations.CheckReturnValue
import io.reactivex.annotations.Experimental
import io.reactivex.annotations.NonNull
import io.reactivex.annotations.Nullable
import io.reactivex.internal.functions.ObjectHelper
import io.reactivex.internal.fuseable.QueueSubscription
import io.reactivex.internal.queue.SpscLinkedArrayQueue
import io.reactivex.internal.subscriptions.BasicIntQueueSubscription
import io.reactivex.internal.subscriptions.EmptySubscription
import io.reactivex.internal.subscriptions.SubscriptionHelper
import io.reactivex.internal.util.BackpressureHelper
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.processors.FlowableProcessor
import io.rsocket.kotlin.Frame
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**Unicast Processor which releases dropped frames*/
internal class FrameUnicastProcessor
@JvmOverloads
internal constructor(capacityHint: Int, onTerminate: Runnable? = null,
                     internal val delayError: Boolean = true) : FlowableProcessor<Frame>() {

    internal val queue: SpscLinkedArrayQueue<Frame> = SpscLinkedArrayQueue(
            ObjectHelper.verifyPositive(capacityHint, "capacityHint"))

    internal val onTerminate: AtomicReference<Runnable> = AtomicReference<Runnable>(onTerminate)

    @Volatile
    internal var done: Boolean = false

    internal var error: Throwable? = null

    internal val actual: AtomicReference<Subscriber<in Frame>> = AtomicReference()

    @Volatile
    internal var cancelled: Boolean = false

    internal val once: AtomicBoolean = AtomicBoolean()

    internal val wip: BasicIntQueueSubscription<Frame> = UnicastQueueSubscription()

    internal val requested: AtomicLong = AtomicLong()

    internal var enableOperatorFusion: Boolean = false

    internal fun doTerminate() {
        val r = onTerminate.getAndSet(null)
        r?.run()
    }

    internal fun drainRegular(a: Subscriber<in Frame>) {
        var missed = 1

        val q = queue
        val failFast = !delayError
        while (true) {

            val r = requested.get()
            var e = 0L

            while (r != e) {
                val d = done

                val t = q.poll()
                val empty = t == null

                if (checkTerminated(failFast, d, empty, a, q)) {
                    return
                }

                if (empty) {
                    break
                }

                a.onNext(t)

                e++
            }

            if (r == e && checkTerminated(failFast, done, q.isEmpty, a, q)) {
                return
            }

            if (e != 0L && r != java.lang.Long.MAX_VALUE) {
                requested.addAndGet(-e)
            }

            missed = wip.addAndGet(-missed)
            if (missed == 0) {
                break
            }
        }
    }

    internal fun drainFused(a: Subscriber<in Frame>) {
        var missed = 1

        val q = queue
        val failFast = !delayError
        while (true) {

            if (cancelled) {
                releaseQueue(q)
                actual.lazySet(null)
                return
            }

            val d = done

            if (failFast && d && error != null) {
                releaseQueue(q)
                actual.lazySet(null)
                a.onError(error)
                return
            }
            a.onNext(null)

            if (d) {
                actual.lazySet(null)

                val ex = error
                if (ex != null) {
                    a.onError(ex)
                } else {
                    a.onComplete()
                }
                return
            }

            missed = wip.addAndGet(-missed)
            if (missed == 0) {
                break
            }
        }
    }

    internal fun drain() {
        if (wip.andIncrement != 0) {
            return
        }

        var missed = 1

        var a: Subscriber<in Frame>? = actual.get()
        while (true) {
            if (a != null) {

                if (enableOperatorFusion) {
                    drainFused(a)
                } else {
                    drainRegular(a)
                }
                return
            }

            missed = wip.addAndGet(-missed)
            if (missed == 0) {
                break
            }
            a = actual.get()
        }
    }

    internal fun checkTerminated(failFast: Boolean,
                                 d: Boolean,
                                 empty: Boolean,
                                 a: Subscriber<in Frame>,
                                 q: SpscLinkedArrayQueue<Frame>): Boolean {
        if (cancelled) {
            releaseQueue(q)
            actual.lazySet(null)
            return true
        }

        if (d) {
            if (failFast && error != null) {
                releaseQueue(q)
                actual.lazySet(null)
                a.onError(error)
                return true
            }
            if (empty) {
                val e = error
                actual.lazySet(null)
                if (e != null) {
                    a.onError(e)
                } else {
                    a.onComplete()
                }
                return true
            }
        }

        return false
    }

    override fun onSubscribe(s: Subscription) {
        if (done || cancelled) {
            s.cancel()
        } else {
            s.request(java.lang.Long.MAX_VALUE)
        }
    }

    override fun onNext(t: Frame) {
        ObjectHelper.requireNonNull(
                t,
                "onNext called with null. Null values are generally " +
                        "not allowed in 2.x operators and sources.")

        if (done || cancelled) {
            t.release()
            return
        }

        queue.offer(t)
        drain()
    }

    override fun onError(t: Throwable) {
        ObjectHelper.requireNonNull(t,
                "onError called with null. Null values are generally " +
                        "not allowed in 2.x operators and sources.")

        if (done || cancelled) {
            RxJavaPlugins.onError(t)
            return
        }

        error = t
        done = true

        doTerminate()

        drain()
    }

    override fun onComplete() {
        if (done || cancelled) {
            return
        }

        done = true

        doTerminate()

        drain()
    }

    override fun subscribeActual(s: Subscriber<in Frame>) {
        if (!once.get() && once.compareAndSet(false, true)) {

            s.onSubscribe(wip)
            actual.set(s)
            if (cancelled) {
                actual.lazySet(null)
            } else {
                drain()
            }
        } else {
            EmptySubscription.error(IllegalStateException(
                    "This processor allows only a single Subscriber"), s)
        }
    }

    internal inner class UnicastQueueSubscription : BasicIntQueueSubscription<Frame>() {
        override fun toByte(): Byte = get().toByte()

        override fun toChar(): Char = get().toChar()

        override fun toShort(): Short = get().toShort()

        @Nullable
        override fun poll(): Frame? {
            return queue.poll()
        }

        override fun isEmpty(): Boolean {
            return queue.isEmpty
        }

        override fun clear() {
            releaseQueue(queue)
        }

        override fun requestFusion(requestedMode: Int): Int {
            if (requestedMode and QueueSubscription.ASYNC != 0) {
                enableOperatorFusion = true
                return QueueSubscription.ASYNC
            }
            return QueueSubscription.NONE
        }

        override fun request(n: Long) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(requested, n)
                drain()
            }
        }

        override fun cancel() {
            if (cancelled) {
                return
            }
            cancelled = true

            doTerminate()

            if (!enableOperatorFusion) {
                if (wip.andIncrement == 0) {
                    releaseQueue(queue)
                    actual.lazySet(null)
                }
            }
        }
    }

    private fun releaseQueue(queue: SpscLinkedArrayQueue<Frame>) {
        var item = queue.poll()
        while (item != null) {
            item.release()
            item = queue.poll()
        }
    }

    override fun hasSubscribers(): Boolean {
        return actual.get() != null
    }

    @Nullable
    override fun getThrowable(): Throwable? {
        return if (done) {
            error
        } else null
    }

    override fun hasComplete(): Boolean {
        return done && error == null
    }

    override fun hasThrowable(): Boolean {
        return done && error != null
    }

    companion object {

        @CheckReturnValue
        @NonNull
        fun create(): FrameUnicastProcessor {
            return FrameUnicastProcessor(Flowable.bufferSize())
        }

        @CheckReturnValue
        @NonNull
        fun create(capacityHint: Int): FrameUnicastProcessor {
            return FrameUnicastProcessor(capacityHint)
        }

        @CheckReturnValue
        @Experimental
        @NonNull
        fun create(delayError: Boolean): FrameUnicastProcessor {
            return FrameUnicastProcessor(Flowable.bufferSize(), null, delayError)
        }

        @CheckReturnValue
        @NonNull
        fun create(capacityHint: Int, onCancelled: Runnable): FrameUnicastProcessor {
            ObjectHelper.requireNonNull(onCancelled, "onTerminate")
            return FrameUnicastProcessor(capacityHint, onCancelled)
        }

        @CheckReturnValue
        @Experimental
        @NonNull
        fun create(capacityHint: Int,
                   onCancelled: Runnable,
                   delayError: Boolean): FrameUnicastProcessor {
            ObjectHelper.requireNonNull(onCancelled, "onTerminate")
            return FrameUnicastProcessor(capacityHint, onCancelled, delayError)
        }
    }
}