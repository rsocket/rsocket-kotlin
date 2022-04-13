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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.io.*

internal fun ByteReadPacket.withLength(): ByteReadPacket = buildPacket {
    @Suppress("INVISIBLE_MEMBER") writeLength(this@withLength.remaining.toInt())
    writePacket(this@withLength)
}

internal class FrameWithLengthAssembler(private val onFrame: (frame: ByteReadPacket) -> Unit) {
    private var expectedFrameLength = 0 //TODO atomic for native
    private val packetBuilder: BytePacketBuilder = BytePacketBuilder()
    inline fun write(write: BytePacketBuilder.() -> Unit) {
        packetBuilder.write()
        loop()
    }

    private fun loop() {
        while (true) when {
            expectedFrameLength == 0 && packetBuilder.size < 3 -> return // no length
            expectedFrameLength == 0                           -> withTemp { // has length
                expectedFrameLength = @Suppress("INVISIBLE_MEMBER") it.readLength()
                if (it.remaining >= expectedFrameLength) build(it) // if has length and frame
            }
            packetBuilder.size < expectedFrameLength           -> return // not enough bytes to read frame
            else                                               -> withTemp { build(it) } // enough bytes to read frame
        }
    }

    private fun build(from: ByteReadPacket) {
        val frame = buildPacket {
            writePacket(from, expectedFrameLength)
        }
        expectedFrameLength = 0
        onFrame(frame)
    }

    private inline fun withTemp(block: (tempPacket: ByteReadPacket) -> Unit) {
        val tempPacket = packetBuilder.build()
        block(tempPacket)
        packetBuilder.writePacket(tempPacket)
    }
}
