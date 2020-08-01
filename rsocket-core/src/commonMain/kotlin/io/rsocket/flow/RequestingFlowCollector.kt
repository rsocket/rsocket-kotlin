package io.rsocket.flow

import kotlinx.coroutines.flow.*

class RequestingFlowCollector<T>
@PublishedApi
internal constructor(
    private val flowCollector: FlowCollector<T>,
    internal val requestStrategy: RequestStrategy
) {
    val initialRequest: Int = requestStrategy.initialRequest

    init {
        require(initialRequest > 0) { "Initial request must be positive, but was '$initialRequest'" }
    }

    suspend inline fun emit(value: T, request: (n: Int) -> Unit = {}) {
        val n = emitAndRequest(value)
        if (n > 0) request(n)
    }

    @PublishedApi
    internal suspend fun emitAndRequest(value: T): Int {
        val n = requestStrategy.requestOnEmit()
        flowCollector.emit(value)
        return n
    }
}
