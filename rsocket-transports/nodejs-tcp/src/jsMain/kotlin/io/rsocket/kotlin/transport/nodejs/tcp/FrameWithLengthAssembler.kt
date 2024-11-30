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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.io.*
import org.khronos.webgl.*

private fun ByteArray.toUint8Array(): Uint8Array {
    val int8Array = unsafeCast<Int8Array>()
    return Uint8Array(int8Array.buffer, int8Array.byteOffset, int8Array.length)
}

private fun Uint8Array.toByteArray(): ByteArray {
    return Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()
}

internal fun Socket.writeFrame(frame: Buffer) {
    val packet = Buffer().apply {
        writeInt24(frame.size.toInt())
        transferFrom(frame)
    }
    write(packet.readByteArray().toUint8Array())
}

internal class FrameWithLengthAssembler(private val onFrame: (frame: Buffer) -> Unit) : AutoCloseable {
    private var closed = false
    private var expectedFrameLength = 0
    private val buffer = Buffer()

    override fun close() {
        buffer.clear()
        closed = true
    }

    fun write(array: Uint8Array) {
        if (closed) return
        buffer.write(array.toByteArray())
        loop()
    }

    private fun loop() {
        while (true) when {
            // no length
            expectedFrameLength == 0 && buffer.size < 3 -> return
            // has length
            expectedFrameLength == 0                    -> expectedFrameLength = buffer.readInt24()
            // not enough bytes to read frame
            buffer.size < expectedFrameLength           -> return
            // enough bytes to read frame
            else                                        -> {
                val frame = Buffer()
                buffer.readTo(frame, expectedFrameLength.toLong())
                expectedFrameLength = 0
                onFrame(frame)
            }
        }
    }
}
