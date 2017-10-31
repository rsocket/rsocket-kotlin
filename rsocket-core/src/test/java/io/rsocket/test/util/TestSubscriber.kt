/*
package io.rsocket.test.util

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock

import io.rsocket.Payload
import org.mockito.Mockito
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

object TestSubscriber {
    fun <T> create(): Subscriber<T>? {
        return create(java.lang.Long.MAX_VALUE)
    }

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

    fun anyPayload(): Payload {
        return any(Payload::class.java)
    }

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
*/
