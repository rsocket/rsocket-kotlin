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

package io.rsocket.kotlin.test.util

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock

import io.rsocket.kotlin.Payload
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
