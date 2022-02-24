package io.rsocket.kotlin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@ExperimentalStreamsApi
public sealed interface FlowSubscribeScope : CoroutineScope, FlowRequester

@ExperimentalStreamsApi
public interface FlowSubscriber {
    public suspend fun onEmit() {}
    public suspend fun onCompletion(cause: Throwable?) {}
}

//reusable and overridable request strategy
public fun interface FlowRequestStrategy {
    @ExperimentalStreamsApi
    public suspend fun FlowSubscribeScope.subscribe(): FlowSubscriber?

    //TODO: may be add another out of the box request strategy?
    @Suppress("FunctionName")
    public companion object {
        public fun RequestAll(): FlowRequestStrategy = FlowRequestAllStrategy

        public fun RequestBy(requestSize: Int, requestOn: Int = requestSize / 4): FlowRequestStrategy =
            FlowRequestByStrategy(requestSize, requestOn)

        internal fun DefaultStrategy(): FlowRequestStrategy = RequestBy(64, 16)
    }
}

//reusable strategy
public fun <T> Flow<T>.requestWith(strategy: FlowRequestStrategy): Flow<T> =
    FlowWithRequestStrategy(this, strategy)

public fun <T> Flow<T>.requestAll(): Flow<T> = requestWith(FlowRequestStrategy.RequestAll())

public fun <T> Flow<T>.requestBy(requestSize: Int, requestOn: Int = requestSize / 4): Flow<T> =
    requestWith(FlowRequestStrategy.RequestBy(requestSize, requestOn))

@OptIn(ExperimentalStreamsApi::class)
private object FlowRequestAllStrategy : FlowRequestStrategy {
    override suspend fun FlowSubscribeScope.subscribe(): FlowSubscriber? {
        request(Int.MAX_VALUE)
        return null
    }
}

@OptIn(ExperimentalStreamsApi::class)
private class FlowRequestByStrategy(
    private val requestSize: Int,
    private val requestOn: Int,
) : FlowRequestStrategy {

    init {
        require(requestOn in 0 until requestSize) {
            "requestSize and requestOn should be in relation: requestSize > requestOn >= 0"
        }
    }

    override suspend fun FlowSubscribeScope.subscribe(): FlowSubscriber {
        request(requestSize)
        return object : FlowSubscriber {
            private var requested = requestSize
            override suspend fun onEmit() {
                requested -= 1
                if (requested == requestOn) {
                    requested += requestSize
                    request(requestSize)
                }
            }
        }
    }
}
