package io.rsocket.kotlin.transport.ktor.tcp

import kotlinx.coroutines.*

internal actual val defaultDispatcher: CoroutineDispatcher get() = Dispatchers.Unconfined
