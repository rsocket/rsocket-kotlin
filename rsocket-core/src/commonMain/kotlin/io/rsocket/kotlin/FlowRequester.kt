package io.rsocket.kotlin

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

@ExperimentalStreamsApi
public sealed interface FlowRequester {
    public fun request(n: Int)
}

@ExperimentalStreamsApi
public sealed interface FlowWithRequester<T> : Flow<T>, FlowRequester

//single use, can be used only once per flow
@ExperimentalStreamsApi
public inline fun <T, R> Flow<T>.withRequester(block: FlowWithRequester<T>.() -> R): R {
    val flow = FlowWithRequesterImpl(this)
    val result = flow.block()
    flow.checkCollected()
    return result
}

@ExperimentalStreamsApi //should be called just after request, before transformations
public fun <T> Flow<T>.requestOnly(n: Int): Flow<T> = flow {
    withRequester {
        request(n)
        emitAll(take(n))
    }
}

@PublishedApi
@ExperimentalStreamsApi
internal class FlowWithRequesterImpl<T>(private val originalFlow: Flow<T>) : FlowWithRequester<T>, FlowRequesterImpl() {
    private val collected = atomic(false)

    fun checkCollected() {
        check(collected.value) { "FlowWithRequester should be collected in place" }
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        val currentContext = currentCoroutineContext()

        check(collected.compareAndSet(false, true)) { "FlowWithRequester can be collected only once" }
        check(currentContext[FlowRequestsHolder] == null) { "Flow.withRequester can be used only once per flow" }
        check(currentContext[FlowRequestStrategyHolder] == null) { "FlowRequestStrategy should not be used after Flow.withRequester" }

        emitAllWithRequests(originalFlow, collector)
    }
}

internal inline fun <T> flowWithRequests(
    crossinline block: suspend FlowCollector<T>.(requests: ReceiveChannel<Int>) -> Unit,
): Flow<T> = object : FlowWithRequests<T>() {
    override suspend fun collectWithRequests(collector: FlowCollector<T>, requests: ReceiveChannel<Int>) =
        collector.block(requests)
}

internal abstract class FlowWithRequests<T> : Flow<T> {
    private val collected = atomic(false)

    abstract suspend fun collectWithRequests(collector: FlowCollector<T>, requests: ReceiveChannel<Int>)

    @OptIn(ExperimentalStreamsApi::class)
    final override suspend fun collect(collector: FlowCollector<T>) {
        val context = currentCoroutineContext()

        val requests = context[FlowRequestsHolder]?.requests

        if (requests != null) {
            check(!collected.getAndSet(true)) { "FlowWithRequests can be collected only once" }
            return collectWithRequests(collector, requests)
        }

        with(context[FlowRequestStrategyHolder]?.strategy ?: FlowRequestStrategy.DefaultStrategy()) {
            coroutineScope {
                val scope = FlowSubscriberScopeImpl(coroutineContext)

                val flow = when (val subscriber = scope.subscribe()) {
                    null -> this@FlowWithRequests
                    else -> withSubscriber(subscriber)
                }

                scope.emitAllWithRequests(flow, collector)
            }
        }
    }
}

@ExperimentalStreamsApi
internal class FlowSubscriberScopeImpl(
    override val coroutineContext: CoroutineContext,
) : FlowSubscribeScope, FlowRequesterImpl()

@ExperimentalStreamsApi
private fun <T> Flow<T>.withSubscriber(subscriber: FlowSubscriber): Flow<T> = flow {
    try {
        collect {
            subscriber.onEmit()
            emit(it)
        }
        subscriber.onCompletion(null)
    } catch (cause: Throwable) {
        subscriber.onCompletion(cause)
        throw cause
    }
}

@ExperimentalStreamsApi
internal sealed class FlowRequesterImpl : FlowRequester {
    private val requests = Channel<Int>(Channel.UNLIMITED)

    suspend fun <T> emitAllWithRequests(flow: Flow<T>, collector: FlowCollector<T>) {
        collector.emitAll(flow.flowOn(FlowRequestsHolder(requests)))
    }

    final override fun request(n: Int) {
        requests.trySend(n)
    }
}

internal class FlowWithRequestStrategy<T>(
    private val originalFlow: Flow<T>,
    private val strategy: FlowRequestStrategy,
) : Flow<T> {
    @OptIn(ExperimentalStreamsApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        val currentContext = currentCoroutineContext()
        //if we already have one of holders in context - ignore new strategy - the latest used wins
        when {
            currentContext[FlowRequestStrategyHolder] == null &&
                    currentContext[FlowRequestsHolder] == null -> originalFlow.flowOn(FlowRequestStrategyHolder(strategy))
            else                                               -> originalFlow
        }.collect(collector)
    }
}

private class FlowRequestsHolder(val requests: ReceiveChannel<Int>) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<FlowRequestsHolder>
}

private class FlowRequestStrategyHolder(val strategy: FlowRequestStrategy) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<FlowRequestStrategyHolder>
}
