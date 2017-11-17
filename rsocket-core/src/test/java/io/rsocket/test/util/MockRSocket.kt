/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.rsocket.test.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`

import io.rsocket.Payload
import io.rsocket.RSocket
import java.util.concurrent.atomic.AtomicInteger
import org.reactivestreams.Publisher

class MockRSocket(private val delegate: RSocket) : RSocket {

    private val fnfCount: AtomicInteger = AtomicInteger()
    private val rrCount: AtomicInteger = AtomicInteger()
    private val rStreamCount: AtomicInteger = AtomicInteger()
    private val rSubCount: AtomicInteger = AtomicInteger()
    private val rChannelCount: AtomicInteger = AtomicInteger()
    private val pushCount: AtomicInteger = AtomicInteger()

    override fun fireAndForget(payload: Payload): Completable {
        return delegate.fireAndForget(payload).doOnSubscribe { fnfCount.incrementAndGet() }
    }

    override fun requestResponse(payload: Payload): Single<Payload> {
        return delegate.requestResponse(payload).doOnSubscribe { rrCount.incrementAndGet() }
    }

    override fun requestStream(payload: Payload): Flowable<Payload> {
        return delegate.requestStream(payload).doOnSubscribe { rStreamCount.incrementAndGet() }
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return delegate.requestChannel(payloads).doOnSubscribe { rChannelCount.incrementAndGet() }
    }

    override fun metadataPush(payload: Payload): Completable {
        return delegate.metadataPush(payload).doOnSubscribe { pushCount.incrementAndGet() }
    }

    override fun availability(): Double = delegate.availability()

    override fun close(): Completable = delegate.close()

    override fun onClose(): Completable = delegate.onClose()

    fun assertFireAndForgetCount(expected: Int) {
        assertCount(expected, "fire-and-forget", fnfCount)
    }

    fun assertRequestResponseCount(expected: Int) {
        assertCount(expected, "request-response", rrCount)
    }

    fun assertRequestStreamCount(expected: Int) {
        assertCount(expected, "request-stream", rStreamCount)
    }

    fun assertRequestSubscriptionCount(expected: Int) {
        assertCount(expected, "request-subscription", rSubCount)
    }

    fun assertRequestChannelCount(expected: Int) {
        assertCount(expected, "request-channel", rChannelCount)
    }

    fun assertMetadataPushCount(expected: Int) {
        assertCount(expected, "metadata-push", pushCount)
    }

    private fun assertCount(expected: Int, type: String, counter: AtomicInteger) {
        assertThat("Unexpected invocations for $type.", counter.get(), `is`(expected))
    }
}

