/*
 * Copyright 2015-2022 the original author or authors.
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

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*

public fun PayloadMimeType(
    data: MimeTypeWithName,
    metadata: MimeTypeWithName,
): PayloadMimeType = PayloadMimeType(data.text, metadata.text)

public class PayloadMimeType(
    public val data: String,
    public val metadata: String,
) {
    init {
        data.requireAscii()
        metadata.requireAscii()
    }
}

internal val DefaultPayloadMimeType = PayloadMimeType(
    data = WellKnownMimeType.ApplicationOctetStream,
    metadata = WellKnownMimeType.ApplicationOctetStream
)
