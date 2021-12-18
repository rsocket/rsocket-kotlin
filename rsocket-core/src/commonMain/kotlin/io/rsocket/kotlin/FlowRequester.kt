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

@ExperimentalStreamsApi //single use
public sealed interface FlowWithRequester<T> : Flow<T>, FlowRequester

@ExperimentalStreamsApi //reusable
public fun interface FlowRequestStrategy {
    public suspend fun FlowRequestStrategyConfiguration.configure()

    @Suppress("FunctionName")
    public companion object { //TODO may be add another out of the box request strategy?
        public fun RequestAll(): FlowRequestStrategy = FlowRequestAllStrategy

        public fun RequestBy(requestSize: Int, requestOn: Int = requestSize / 4): FlowRequestStrategy =
            FlowRequestByStrategy(requestSize, requestOn)
    }
}

@ExperimentalStreamsApi
public sealed interface FlowRequestStrategyConfiguration : CoroutineScope, FlowRequester {
    public fun onEach(block: suspend () -> Unit) //TODO do we need more callbacks here?
}

//reusable strategy
@ExperimentalStreamsApi
public fun <T> Flow<T>.requestWith(strategy: FlowRequestStrategy): Flow<T> =
    FlowWithRequestStrategy(this, strategy)

@ExperimentalStreamsApi
public fun <T> Flow<T>.requestAll(): Flow<T> =
    requestWith(FlowRequestStrategy.RequestAll())

@ExperimentalStreamsApi
public fun <T> Flow<T>.requestBy(requestSize: Int, requestOn: Int = requestSize / 4): Flow<T> =
    requestWith(FlowRequestStrategy.RequestBy(requestSize, requestOn))

//fully custom requesting strategy
//single use, can be used only once per flow
@ExperimentalStreamsApi
public inline fun <T, R> Flow<T>.withRequester(block: FlowWithRequester<T>.() -> R): R {
    val flow = FlowWithRequesterImpl(this)
    val result = flow.block()
    flow.checkCollected()
    return result
}

//private fun <T> Flow<T>.requestOnly(n: Int): Flow<T> = flow {
//    withRequester {
//        request(n)
//        emitAll(take(n))
//    }
//}

internal inline fun <T> flowWithRequests(
    crossinline block: suspend FlowCollector<T>.(requests: ReceiveChannel<Int>) -> Unit
): Flow<T> = object : FlowWithRequests<T>() {
    override suspend fun collect(collector: FlowCollector<T>, requests: ReceiveChannel<Int>) = collector.block(requests)
}

internal abstract class FlowWithRequests<T> : Flow<T> {
    private val collected = atomic(false)

    abstract suspend fun collect(collector: FlowCollector<T>, requests: ReceiveChannel<Int>)

    @InternalCoroutinesApi
    @OptIn(ExperimentalStreamsApi::class)
    final override suspend fun collect(collector: FlowCollector<T>) {
        val context = currentCoroutineContext()
        when (val requests = context[FlowRequestsHolder]?.requests) {
            null -> when (val strategy = context[FlowRequestStrategyHolder]?.strategy) {
                null -> requestBy(64).collect(collector) //provide default strategy
                else -> collector.emitAllWithStrategy(this, strategy) //convert strategy to requester
            }
            else -> {
                //use requests from requester
                check(!collected.getAndSet(true)) { "FlowWithRequests can be collected just once" }
                collect(collector, requests)
            }
        }
    }
}

@ExperimentalStreamsApi
private suspend fun <T> FlowCollector<T>.emitAllWithStrategy(
    flow: Flow<T>,
    strategy: FlowRequestStrategy
): Unit = coroutineScope {
    flow.withRequester {
        val configuration = FlowRequestStrategyConfigurationImpl(coroutineContext, this)
        with(strategy) { configuration.configure() }

        emitAll(
            when (val onEach = configuration.onEach) {
                null -> this
                else -> onEach { onEach() }
            }
        )
    }
}

@ExperimentalStreamsApi
private class FlowRequestStrategyConfigurationImpl(
    override val coroutineContext: CoroutineContext,
    private val requester: FlowRequester
) : FlowRequestStrategyConfiguration {
    var onEach: (suspend () -> Unit)? = null
    override fun request(n: Int) {
        requester.request(n)
    }

    override fun onEach(block: suspend () -> Unit) {
        check(onEach == null)
        onEach = block
    }
}

@PublishedApi
@ExperimentalStreamsApi
internal class FlowWithRequesterImpl<T>(private val originalFlow: Flow<T>) : FlowWithRequester<T> {
    private val collected = atomic(false)
    private val requests = Channel<Int>(Channel.UNLIMITED)

    fun checkCollected() {
        check(collected.value) { "Flow.withRequester should be collected." }
    }

    override fun request(n: Int) {
        requests.trySend(n)
    }

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<T>) {
        check(collected.compareAndSet(false, true)) { "Flow.withRequester can be collected only once." }
        check(currentCoroutineContext()[FlowRequestsHolder] == null) { "Flow.withRequester can be used only once per flow" }

        originalFlow.flowOn(FlowRequestsHolder(requests)).collect(collector)
    }
}

@ExperimentalStreamsApi
private class FlowWithRequestStrategy<T>(
    private val originalFlow: Flow<T>,
    private val strategy: FlowRequestStrategy
) : Flow<T> {
    @InternalCoroutinesApi
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

@ExperimentalStreamsApi
private class FlowRequestsHolder(val requests: ReceiveChannel<Int>) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<FlowRequestsHolder>
}

@ExperimentalStreamsApi
private class FlowRequestStrategyHolder(val strategy: FlowRequestStrategy) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<FlowRequestStrategyHolder>
}

@ExperimentalStreamsApi
private class FlowRequestByStrategy(
    private val requestSize: Int,
    private val requestOn: Int
) : FlowRequestStrategy {

    init {
        require(requestOn in 0 until requestSize) {
            "requestSize and requestOn should be in relation: requestSize > requestOn >= 0"
        }
    }

    override suspend fun FlowRequestStrategyConfiguration.configure() {
        request(requestSize)
        var requested = requestSize
        onEach {
            requested -= 1
            if (requested == requestOn) {
                requested += requestSize
                request(requestSize)
            }
        }
    }
}

@ExperimentalStreamsApi
private object FlowRequestAllStrategy : FlowRequestStrategy {
    override suspend fun FlowRequestStrategyConfiguration.configure() {
        request(Int.MAX_VALUE)
    }
}
