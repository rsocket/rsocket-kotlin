/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.Payload
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

internal class StreamReceiver private constructor() : FlowableProcessor<Payload>() {
    private var cancelled = false
    private val delegate: FlowableProcessor<Payload> = UnicastProcessor
            .create<Payload>(128) { cancelled = true }

    fun doOnRequestIfActive(onRequest: (Long) -> Unit): Flowable<Payload> {
        return delegate.doOnRequest { request ->
            if (!terminated()) {
                onRequest(request)
            }
        }
    }

    override fun hasThrowable(): Boolean = delegate.hasThrowable()

    override fun onComplete() = delegate.onComplete()

    override fun hasSubscribers(): Boolean = delegate.hasSubscribers()

    override fun onSubscribe(s: Subscription) = delegate.onSubscribe(s)

    override fun onError(t: Throwable) = delegate.onError(t)

    override fun getThrowable(): Throwable? = delegate.throwable

    override fun subscribeActual(s: Subscriber<in Payload>) = delegate.subscribe(s)

    override fun onNext(t: Payload) = delegate.onNext(t)

    override fun hasComplete(): Boolean = delegate.hasComplete()

    private fun terminated(): Boolean = hasComplete() or hasThrowable() or cancelled

    companion object {
        fun create(): StreamReceiver = StreamReceiver()
    }
}