/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*

public interface RSocket : CoroutineScope {

    public suspend fun metadataPush(metadata: Buffer) {
        metadata.clear()
        notImplemented("Metadata Push")
    }

    public suspend fun fireAndForget(payload: Payload) {
        payload.close()
        notImplemented("Fire and Forget")
    }

    public suspend fun requestResponse(payload: Payload): Payload {
        payload.close()
        notImplemented("Request Response")
    }

    public fun requestStream(payload: Payload): Flow<Payload> {
        payload.close()
        notImplemented("Request Stream")
    }

    public fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        initPayload.close()
        notImplemented("Request Channel")
    }
}

private fun notImplemented(operation: String): Nothing = throw NotImplementedError("$operation is not implemented.")

/**
 * Tries to emit [value], if emit failed, f.e. due cancellation, calls [Closeable.close] on [value].
 * Better to use it instead of [FlowCollector.emit] with [Payload] or [ByteReadPacket] to avoid leaks of dropped elements.
 */
@Deprecated(
    message = "Will be removed in next release",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("emit(value)")
)
public suspend fun <C : AutoCloseable> FlowCollector<C>.emitOrClose(value: C) {
    try {
        return emit(value)
    } catch (e: Throwable) {
        value.close()
        throw e
    }
}
