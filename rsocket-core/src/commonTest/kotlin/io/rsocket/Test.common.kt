package io.rsocket

import kotlinx.coroutines.*
import kotlin.time.*

expect fun test(timeout: Duration? = 10.seconds, block: suspend CoroutineScope.() -> Unit)
