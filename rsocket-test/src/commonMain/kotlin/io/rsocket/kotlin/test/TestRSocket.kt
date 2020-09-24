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

package io.rsocket.kotlin.test

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*

class TestRSocket : RSocket {
    override val job: Job = Job()

    override suspend fun metadataPush(metadata: ByteReadPacket): Unit = metadata.release()

    override suspend fun fireAndForget(payload: Payload): Unit = payload.release()

    override suspend fun requestResponse(payload: Payload): Payload {
        payload.release()
        return Payload(data, metadata)
    }

    override fun requestStream(payload: Payload): Flow<Payload> = flow {
        repeat(10000) { emit(requestResponse(payload)) }
    }

    override fun requestChannel(payloads: Flow<Payload>): Flow<Payload> = flow {
        payloads.collect { emit(it) }
    }

    companion object {
        const val data = "hello world"
        const val metadata = "metadata"
    }
}
