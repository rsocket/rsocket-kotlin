/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.kotlin.internal.fragmentation

import io.netty.buffer.PooledByteBufAllocator
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight
import java.util.concurrent.atomic.AtomicBoolean

/** Assembles Fragmented frames.  */
internal class StreamFramesReassembler(frame: Frame) : Disposable {

    private val isDisposed = AtomicBoolean()
    private val blueprintFrame = frame
    private val dataBuffer = PooledByteBufAllocator.DEFAULT.compositeBuffer()
    private val metadataBuffer =
            if (frame.hasMetadata())
                PooledByteBufAllocator.DEFAULT.compositeBuffer()
            else
                null

    fun append(frame: Frame): StreamFramesReassembler {
        val byteBuf = frame.content()
        val frameType = FrameHeaderFlyweight.frameType(byteBuf)
        val frameLength = FrameHeaderFlyweight.frameLength(byteBuf)
        val metadataLength = FrameHeaderFlyweight.metadataLength(
                byteBuf,
                frameType,
                frameLength) ?: 0
        val dataLength = FrameHeaderFlyweight.dataLength(byteBuf, frameType)
        if (metadataLength > 0) {
            var metadataOffset = FrameHeaderFlyweight.metadataOffset(byteBuf)
            if (FrameHeaderFlyweight.hasMetadataLengthField(frameType)) {
                metadataOffset += FrameHeaderFlyweight.FRAME_LENGTH_SIZE
            }
            metadataBuffer!!.addComponent(
                    true,
                    byteBuf.slice(metadataOffset, metadataLength))
        }
        if (dataLength > 0) {
            val dataOffset = FrameHeaderFlyweight.dataOffset(
                    byteBuf,
                    frameType,
                    frameLength)
            dataBuffer.addComponent(
                    true,
                    byteBuf.slice(dataOffset, dataLength))
        }
        return this
    }

    fun reassemble(): Frame {
        val assembled = Frame.Fragmentation.assembleFrame(
                blueprintFrame,
                metadataBuffer,
                dataBuffer)
        dispose()
        return assembled
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            dataBuffer.release()
            metadataBuffer?.release()
        }
    }

    override fun isDisposed(): Boolean = isDisposed.get()
}
