/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.payload

import io.ktor.utils.io.core.*

public fun Payload(data: ByteReadPacket, metadata: ByteReadPacket? = null): Payload = DefaultPayload(data, metadata)

// TODO: move to root, rename to RSocketPayload, cleanup API regarding creation of a Payload
public sealed interface Payload : Closeable {
    public val data: ByteReadPacket
    public val metadata: ByteReadPacket?

    public fun copy(): Payload = DefaultPayload(data.copy(), metadata?.copy())

    override fun close() {
        data.close()
        metadata?.close()
    }

    public companion object {
        public val Empty: Payload = Payload(ByteReadPacket.Empty)
    }
}

private class DefaultPayload(
    override val data: ByteReadPacket,
    override val metadata: ByteReadPacket?,
) : Payload
