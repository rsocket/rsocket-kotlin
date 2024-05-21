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

internal fun Source.readResumeToken(): Source {
    val length = readShort().toInt() and 0xFFFF
    return readSource(length.toLong())
}

internal fun Sink.writeResumeToken(resumeToken: Source) {
    resumeToken.withLength { source, length ->
        writeShort(length.toShort())
        transferFrom(source)
    }
}

internal fun Source.readSource(): Source {
    return Buffer().also {
        transferTo(it)
    }
}

internal fun Source.readSource(length: Long): Source {
    return Buffer().also {
        readTo(it, length)
    }
}

internal inline fun <T> Source.withLength(block: (Source, Long) -> T): T {
    val temp = Buffer()
    val length = transferTo(temp)
    return block(temp, length)
}
