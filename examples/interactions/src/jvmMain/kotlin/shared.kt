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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.payload.*

/**
 * Simple custom [Payload] factory function with string data and metadata.
 * Has almost no overhead over call to [Payload] constructor with data and metadata as [ByteReadPacket].
 * Similar functions can be created for all needed use cases
 */
fun Payload(data: String, metadata: String? = null): Payload = buildPayload {
    data(data)
    if (metadata != null) metadata(metadata)
}
