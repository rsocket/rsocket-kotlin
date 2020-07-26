package dev.whyoleg.rsocket.flow

import kotlinx.atomicfu.*
import kotlinx.coroutines.*

class LimitStrategy(override val initialRequest: Int) : RequestStrategy {
    private val requests = atomic(initialRequest)
    private val awaiter = atomic<CancellableContinuation<Unit>?>(null)

    override suspend fun requestOnEmit(): Int {
//        println("USE")
        useRequest()
        return 0
    }

    private suspend fun useRequest() {
        if (requests.value <= 0) {
//            println("WAIT")
            suspendCancellableCoroutine<Unit> {
                awaiter.value = it
                if (requests.value != 0) it.resumeSafely()
            }
        }
        requests.decrementAndGet()
    }

    fun saveRequest(n: Int) {
//        println("SAVE")
        requests.getAndAdd(n)
        awaiter.getAndSet(null)?.resumeSafely()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun CancellableContinuation<Unit>.resumeSafely() {
        val token = tryResume(Unit)
        if (token != null) completeResume(token)
    }
}
