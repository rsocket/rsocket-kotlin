package dev.whyoleg.rsocket

import kotlinx.coroutines.*

interface Cancelable {
    val job: Job
}

val Cancelable.isActive: Boolean get() = job.isActive

fun Cancelable.cancel(cause: CancellationException? = null) {
    job.cancel(cause)
}

fun Cancelable.cancel(message: String, cause: Throwable? = null): Unit = cancel(CancellationException(message, cause))
