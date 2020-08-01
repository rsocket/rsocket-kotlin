package io.rsocket

import kotlinx.coroutines.*
import kotlin.time.*

@OptIn(ExperimentalCoroutinesApi::class)
actual fun test(timeout: Duration?, block: suspend CoroutineScope.() -> Unit): dynamic = GlobalScope.promise {
    when (timeout) {
        null -> block()
        else -> withTimeout(timeout) { block() }
    }
}
