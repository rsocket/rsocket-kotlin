package io.rsocket.android.internal

import io.reactivex.Flowable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.Payload
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

class StreamReceiver private constructor() : FlowableProcessor<Payload>() {
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