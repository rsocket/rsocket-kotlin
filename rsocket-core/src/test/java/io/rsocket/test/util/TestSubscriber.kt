package io.rsocket.test.util

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock

import io.rsocket.Payload
import org.mockito.Mockito
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

object TestSubscriber {
    @JvmStatic fun <T> create(): Subscriber<T>? = create(java.lang.Long.MAX_VALUE)

    @Suppress("UNCHECKED_CAST")
    fun <T> create(initialRequest: Long): Subscriber<T>? {
        val mock = mock(Subscriber::class.java) as Subscriber<T>

        Mockito.doAnswer { invocation ->
            if (initialRequest > 0) {
                (invocation.arguments[0] as Subscription).request(initialRequest)
            }
            null
        }
                .`when`<Subscriber<T>>(mock)
                .onSubscribe(any(Subscription::class.java))

        return mock
    }

    @JvmStatic fun anyPayload(): Payload {
        return any(Payload::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun createCancelling(): Subscriber<Payload>? {
        val mock = mock(Subscriber::class.java) as Subscriber<Payload>

        Mockito.doAnswer { invocation ->
            (invocation.arguments[0] as Subscription).cancel()
            null
        }
                .`when`<Subscriber<Payload>>(mock)
                .onSubscribe(any(Subscription::class.java))

        return mock
    }
}
