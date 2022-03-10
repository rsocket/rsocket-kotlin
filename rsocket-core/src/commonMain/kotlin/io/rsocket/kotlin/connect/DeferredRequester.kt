package io.rsocket.kotlin.connect

import io.rsocket.kotlin.*
import io.rsocket.kotlin.configuration.*
import kotlinx.coroutines.*

internal class DeferredRequester(
    private val configurationState: ConfigurationState,
    private val deferredRequester: Deferred<RSocket>,
) : DelayedRequester() {
    override suspend fun get(): RSocket {
        configurationState.checkConfigured()
        return deferredRequester.await()
    }
}
