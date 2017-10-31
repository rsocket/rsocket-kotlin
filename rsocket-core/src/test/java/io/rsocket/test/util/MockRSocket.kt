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
 *//*


package io.rsocket.test.util

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`

import io.rsocket.Payload
import io.rsocket.RSocket
import java.util.concurrent.atomic.AtomicInteger
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MockRSocket(private val delegate: RSocket) : RSocket {

    private val fnfCount: AtomicInteger
    private val rrCount: AtomicInteger
    private val rStreamCount: AtomicInteger
    private val rSubCount: AtomicInteger
    private val rChannelCount: AtomicInteger
    private val pushCount: AtomicInteger

    init {
        fnfCount = AtomicInteger()
        rrCount = AtomicInteger()
        rStreamCount = AtomicInteger()
        rSubCount = AtomicInteger()
        rChannelCount = AtomicInteger()
        pushCount = AtomicInteger()
    }

    override fun fireAndForget(payload: Payload): Mono<Void> {
        return delegate.fireAndForget(payload).doOnSubscribe { s -> fnfCount.incrementAndGet() }
    }

    override fun requestResponse(payload: Payload): Mono<Payload> {
        return delegate.requestResponse(payload).doOnSubscribe { s -> rrCount.incrementAndGet() }
    }

    override fun requestStream(payload: Payload): Flux<Payload> {
        return delegate.requestStream(payload).doOnSubscribe { s -> rStreamCount.incrementAndGet() }
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
        return delegate.requestChannel(payloads).doOnSubscribe { s -> rChannelCount.incrementAndGet() }
    }

    override fun metadataPush(payload: Payload): Mono<Void> {
        return delegate.metadataPush(payload).doOnSubscribe { s -> pushCount.incrementAndGet() }
    }

    override fun availability(): Double {
        return delegate.availability()
    }

    override fun close(): Mono<Void> {
        return delegate.close()
    }

    override fun onClose(): Mono<Void> {
        return delegate.onClose()
    }

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
*/
