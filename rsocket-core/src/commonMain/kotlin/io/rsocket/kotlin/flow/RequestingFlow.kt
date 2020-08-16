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

package io.rsocket.kotlin.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.experimental.*

@OptIn(ExperimentalTypeInference::class, FlowPreview::class)
class RequestingFlow<T>(
    private val defaultStrategy: () -> RequestStrategy = RequestStrategy.Default,
    @BuilderInference
    @PublishedApi
    internal val block: suspend RequestingFlowCollector<T>.() -> Unit
) : AbstractFlow<T>() {

    @PublishedApi
    internal suspend fun collectRequesting(collector: RequestingFlowCollector<T>) {
        collector.block()
    }

    override suspend fun collectSafely(collector: FlowCollector<T>) {
        collectRequesting(RequestingFlowCollector(collector, defaultStrategy()))
    }
}

inline fun <T> RequestingFlow<T>.requesting(crossinline strategy: () -> RequestStrategy): Flow<T> = flow {
    collectRequesting(RequestingFlowCollector(this, strategy()))
}

fun <T> RequestingFlow<T>.requesting(strategy: RequestStrategy): Flow<T> = requesting { strategy }

inline fun <T, R> RequestingFlow<T>.intercept(
    noinline request: suspend (n: Int) -> Unit = {},
    crossinline block: Flow<T>.() -> Flow<R>
): RequestingFlow<R> = RequestingFlow {
    this@intercept.block().collect { value ->
        emit(value) { request(it) }
    }
}

suspend fun <T> RequestingFlow<T>.collect(strategy: RequestStrategy, block: suspend (value: T) -> Unit) {
    requesting(strategy).collect(block)
}

fun <T> Flow<T>.onRequest(
    block: suspend (n: Int) -> Unit = {}
): RequestingFlow<T> = RequestingFlow {
    collect { value ->
        emit(value) { block(it) }
    }
}
