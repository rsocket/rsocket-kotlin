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

package io.rsocket.kotlin.frame.io

import kotlinx.io.*

internal fun Source.readResumeToken(): Buffer {
    val length = readShort().toInt() and 0xFFFF
    return readBuffer(length)
}

internal fun Sink.writeResumeToken(resumeToken: Buffer?) {
    resumeToken?.let {
        writeShort(it.size.toShort())
        transferFrom(it)
    }
}

internal fun Source.readBuffer(): Buffer {
    return Buffer().also(this::transferTo)
}

internal fun Source.readBuffer(length: Int): Buffer {
    val output = Buffer()
    output.write(this, length.toLong())
    return output
}

internal val EmptyBuffer: Buffer = Buffer()
