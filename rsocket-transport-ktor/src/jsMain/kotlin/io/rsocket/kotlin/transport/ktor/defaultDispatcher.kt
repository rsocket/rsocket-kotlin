package io.rsocket.kotlin.transport.ktor

import kotlinx.coroutines.*

internal actual val defaultDispatcher: CoroutineDispatcher get() = Dispatchers.Default
