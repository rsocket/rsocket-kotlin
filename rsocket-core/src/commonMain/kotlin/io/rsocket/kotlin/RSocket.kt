/*
 * Copyright 2015-2020 the original author or authors.
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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.flow.*

interface RSocket : Cancelable {

    suspend fun metadataPush(metadata: ByteReadPacket) {
        metadata.release()
        notImplemented("Metadata Push")
    }

    suspend fun fireAndForget(payload: Payload) {
        payload.release()
        notImplemented("Fire and Forget")
    }

    suspend fun requestResponse(payload: Payload): Payload {
        payload.release()
        notImplemented("Request Response")
    }

    fun requestStream(payload: Payload): RequestingFlow<Payload> {
        payload.release()
        notImplemented("Request Stream")
    }

    fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> = notImplemented("Request Channel")
}

fun RSocket.requestChannel(
    payloads: Flow<Payload>,
    block: suspend (n: Int) -> Unit = {}
): RequestingFlow<Payload> = requestChannel(payloads.onRequest(block))

private fun notImplemented(operation: String): Nothing = throw NotImplementedError("$operation is not implemented.")
