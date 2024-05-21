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

import kotlinx.io.*

public fun Payload(data: Source, metadata: Source? = null): Payload = DefaultPayload(data, metadata)

public sealed interface Payload : AutoCloseable {
    public val data: Source
    public val metadata: Source?

    public companion object {
        public val Empty: Payload = Payload(EmptySource)
    }
}

public sealed interface CopyablePayload : Payload {
    public override val data: Buffer
    public override val metadata: Buffer?
    public fun copy(): CopyablePayload
}

public fun Payload.copyable(): CopyablePayload = TODO()

private class DefaultPayload(
    override val data: Source,
    override val metadata: Source?,
) : Payload {
    override fun close() {
        data.close()
        metadata?.close()
    }
}

internal val EmptySource: Source = Buffer()
