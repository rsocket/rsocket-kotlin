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

package io.rsocket.kotlin.test

import io.rsocket.kotlin.payload.*
import kotlinx.io.*
import kotlin.test.*

fun packet(block: Sink.() -> Unit): Buffer = Buffer().apply(block)

fun packet(text: String): Buffer = packet { writeString(text) }

fun packet(array: ByteArray): Buffer = packet { write(array) }

fun payload(data: ByteArray, metadata: ByteArray? = null): Payload = Payload(packet(data), metadata?.let(::packet))

fun payload(data: String, metadata: String? = null): Payload = Payload(packet(data), metadata?.let(::packet))

fun assertBytesEquals(expected: ByteArray?, actual: ByteArray?) {
    assertEquals(expected?.toHexString(), actual?.toHexString())
}
