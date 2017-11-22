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

package io.rsocket.android.internal

import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.internal.util.BackpressureHelper
import java.util.concurrent.atomic.AtomicBoolean
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/**  */
class LimitableRequestPublisher<T> private constructor(private val source: Publisher<T>) : Flowable<T>(), Subscription,Disposable {

    private val canceled: AtomicBoolean

    private var internalRequested: Long = 0

    private var externalRequested: Long = 0

    @Volatile private var subscribed: Boolean = false

    @Volatile private var internalSubscription: Subscription? = null

    init {
        this.canceled = AtomicBoolean()
    }

    override fun subscribeActual(destination: Subscriber<in T>) {
        synchronized(this) {
            if (subscribed) {
                throw IllegalStateException("only one subscriber at a time")
            }

            subscribed = true
        }

        destination.onSubscribe(InnerSubscription())
        source.subscribe(InnerSubscriber(destination))
    }

    fun increaseRequestLimit(n: Long) {
        synchronized(this) {
            externalRequested = BackpressureHelper.addCap(n, externalRequested)
        }

        requestN()
    }

    override fun request(n: Long) {
        increaseRequestLimit(n)
    }

    private fun requestN() {
        var r: Long
        synchronized(this) {
            if (internalSubscription == null) {
                return
            }

            r = Math.min(internalRequested, externalRequested)
            externalRequested -= r
            internalRequested -= r
            if (r > 0) {
                internalSubscription!!.request(r)
            }
        }
    }

    override fun isDisposed(): Boolean = canceled.get()

    override fun dispose() = cancel()

    override fun cancel() {
        if (canceled.compareAndSet(false, true) && internalSubscription != null) {
            internalSubscription!!.cancel()
            internalSubscription = null
            subscribed = false
        }
    }

    private inner class InnerSubscriber internal constructor(internal var destination: Subscriber<in T>) : Subscriber<T> {

        override fun onSubscribe(s: Subscription) {
            synchronized(this@LimitableRequestPublisher) {
                this@LimitableRequestPublisher.internalSubscription = s

                if (canceled.get()) {
                    s.cancel()
                    subscribed = false
                    this@LimitableRequestPublisher.internalSubscription = null
                }
            }

            requestN()
        }

        override fun onNext(t: T) {
            try {
                destination.onNext(t)
            } catch (e: Throwable) {
                onError(e)
            }

        }

        override fun onError(t: Throwable) {
            destination.onError(t)
        }

        override fun onComplete() {
            destination.onComplete()
        }
    }

    private inner class InnerSubscription : Subscription {
        override fun request(n: Long) {
            synchronized(this@LimitableRequestPublisher) {
                internalRequested = BackpressureHelper.addCap(n, internalRequested)
            }

            requestN()
        }

        override fun cancel() {
            this@LimitableRequestPublisher.cancel()
        }
    }

    companion object {

        fun <T> wrap(source: Publisher<T>): LimitableRequestPublisher<T> {
            return LimitableRequestPublisher(source)
        }
    }
}
