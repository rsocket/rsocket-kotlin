package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

//TODO revisit it fully

internal suspend inline fun <T : Closeable> Flow<T>.collectLimiting(
    limiter: Limiter,
    crossinline action: suspend (T) -> Unit
) {
    collect {
        try {
            limiter.useRequest()
            action(it)
        } catch (cause: Throwable) {
            it.close()
            throw cause
        }
    }
}

//TODO revisit 2 atomics and sync object
internal class Limiter(initial: Int) : SynchronizedObject() {
    private val limit = atomic(initial)
    private val awaiter = atomic<CancellableContinuation<Unit>?>(null)

    fun updateRequests(n: Int) {
        if (n <= 0) return
        synchronized(this) {
            limit += n
            awaiter.getAndSet(null)?.takeIf { it.isActive }?.resume(Unit)
        }
    }

    suspend fun useRequest() {
        when (synchronized(this) { limit.getAndDecrement() > 0 }) {
            true  -> currentCoroutineContext().ensureActive() //TODO?
            false -> suspendCancellableCoroutine {
                synchronized(this) {
                    awaiter.value = it
                    if (limit.value >= 0 && it.isActive) it.resume(Unit)
                }
            }
        }
    }
}