package dev.whyoleg.rsocket.flow

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
