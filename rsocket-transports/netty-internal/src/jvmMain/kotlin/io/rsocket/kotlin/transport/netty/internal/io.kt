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

package io.rsocket.kotlin.transport.netty.internal

import io.netty.buffer.*
import kotlinx.io.*
import kotlinx.io.unsafe.*

@OptIn(UnsafeIoApi::class)
public fun ByteBuf.toBuffer(): Buffer {
    val buffer = Buffer()
    while (readableBytes() > 0) {
        val maxToRead = minOf(readableBytes(), UnsafeBufferOperations.maxSafeWriteCapacity)
        UnsafeBufferOperations.writeToTail(buffer, maxToRead) { bytes, start, end ->
            val toRead = minOf(readableBytes(), end - start)
            readBytes(bytes, start, toRead)
            toRead
        }
    }
    release()
    return buffer
}

@OptIn(UnsafeIoApi::class)
public fun Buffer.toByteBuf(allocator: ByteBufAllocator): ByteBuf {
    val nettyBuffer = allocator.directBuffer(size.toInt())
    while (!exhausted()) {
        UnsafeBufferOperations.readFromHead(this) { bytes, start, end ->
            nettyBuffer.writeBytes(bytes, start, end - start)
            end - start
        }
    }
    return nettyBuffer
}
