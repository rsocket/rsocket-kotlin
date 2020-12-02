/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin

import kotlinx.atomicfu.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@SharedImmutable
@ExperimentalStreamsApi
private val DefaultStrategy: RequestStrategy = PrefetchStrategy(64, 16)

@ExperimentalStreamsApi
internal fun CoroutineContext.requestStrategy(): RequestStrategy.Element = (get(RequestStrategy) ?: DefaultStrategy).provide()

@ExperimentalStreamsApi
public interface RequestStrategy : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    public fun provide(): Element

    public interface Element {
        public suspend fun firstRequest(): Int
        public suspend fun nextRequest(): Int
    }

    public companion object Key : CoroutineContext.Key<RequestStrategy>
}


//request `requestSize` when `requestOn` elements left for collection
//f.e. requestSize = 30, requestOn = 10, then first requestN will be 30, after 20 elements will be collected,
//     new requestN for 30 elements will be sent so collect will be smooth
@ExperimentalStreamsApi
public class PrefetchStrategy(
    private val requestSize: Int,
    private val requestOn: Int,
) : RequestStrategy {
    init {
        require(requestOn in 0 until requestSize) { "requestSize and requestOn should be in relation: requestSize > requestOn >= 0" }
    }

    override fun provide(): RequestStrategy.Element = Element(requestSize, requestOn)

    private class Element(
        private val requestSize: Int,
        private val requestOn: Int,
    ) : RequestStrategy.Element {
        private var requested = requestSize
        override suspend fun firstRequest(): Int = requestSize

        override suspend fun nextRequest(): Int {
            requested -= 1
            if (requested != requestOn) return 0

            requested += requestSize
            return requestSize
        }
    }
}

@ExperimentalStreamsApi
public class ChannelStrategy(
    private val channel: ReceiveChannel<Int>,
) : RequestStrategy, RequestStrategy.Element {
    private val used = atomic(false)
    private var requested = 0

    override suspend fun firstRequest(): Int = takePositive()

    override suspend fun nextRequest(): Int {
        requested -= 1
        if (requested != 0) return 0

        val requestSize = takePositive()
        requested += requestSize
        return requestSize
    }

    private suspend fun takePositive(): Int {
        var v = channel.receive()
        while (v <= 0) v = channel.receive()
        return v
    }

    override fun provide(): RequestStrategy.Element {
        if (used.compareAndSet(false, true)) return this
        error("ChannelStrategy can be used only once.")
    }
}
