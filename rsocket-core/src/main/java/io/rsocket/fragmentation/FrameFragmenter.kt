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

package io.rsocket.fragmentation

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.frame.FrameHeaderFlyweight

class FrameFragmenter(private val mtu: Int) {

    fun shouldFragment(frame: Frame): Boolean {
        return isFragmentableFrame(frame.type) && FrameHeaderFlyweight.payloadLength(frame.content()) > mtu
    }

    private fun isFragmentableFrame(type: FrameType): Boolean {
        when (type) {
            FrameType.FIRE_AND_FORGET, FrameType.REQUEST_STREAM, FrameType.REQUEST_CHANNEL, FrameType.REQUEST_RESPONSE, FrameType.PAYLOAD, FrameType.NEXT_COMPLETE, FrameType.METADATA_PUSH -> return true
            else -> return false
        }
    }

    fun fragment(frame: Frame): Flowable<Frame> {

        return Flowable.generate {FragmentGenerator(frame)}
    }

    private inner class FragmentGenerator(frame: Frame) : (Emitter<Frame>) -> Unit {
        private val frame: Frame = frame.retain()
        private val streamId: Int = frame.streamId
        private val frameType: FrameType = frame.type
        private val flags: Int = frame.flags() and FrameHeaderFlyweight.FLAGS_M.inv()
        private val data: ByteBuf = FrameHeaderFlyweight.sliceFrameData(frame.content())
        private val metadata: ByteBuf? = if (frame.hasMetadata()) FrameHeaderFlyweight.sliceFrameMetadata(frame.content()) else null

        override fun invoke(sink: Emitter<Frame>) {
            val dataLength = data.readableBytes()

            if (metadata != null) {
                val metadataLength = metadata.readableBytes()

                if (metadataLength > mtu) {
                    sink.onNext(
                            Frame.PayloadFrame.from(
                                    streamId,
                                    frameType,
                                    metadata.readSlice(mtu),
                                    Unpooled.EMPTY_BUFFER,
                                    flags or FrameHeaderFlyweight.FLAGS_M or FrameHeaderFlyweight.FLAGS_F))
                } else {
                    if (dataLength > mtu - metadataLength) {
                        sink.onNext(
                                Frame.PayloadFrame.from(
                                        streamId,
                                        frameType,
                                        metadata.readSlice(metadataLength),
                                        data.readSlice(mtu - metadataLength),
                                        flags or FrameHeaderFlyweight.FLAGS_M or FrameHeaderFlyweight.FLAGS_F))
                    } else {
                        sink.onNext(
                                Frame.PayloadFrame.from(
                                        streamId,
                                        frameType,
                                        metadata.readSlice(metadataLength),
                                        data.readSlice(dataLength),
                                        flags or FrameHeaderFlyweight.FLAGS_M))
                        frame.release()
                        sink.onComplete()
                    }
                }
            } else {
                if (dataLength > mtu) {
                    sink.onNext(
                            Frame.PayloadFrame.from(
                                    streamId,
                                    frameType,
                                    Unpooled.EMPTY_BUFFER,
                                    data.readSlice(mtu),
                                    flags or FrameHeaderFlyweight.FLAGS_F))
                } else {
                    sink.onNext(
                            Frame.PayloadFrame.from(
                                    streamId, frameType, Unpooled.EMPTY_BUFFER, data.readSlice(dataLength), flags))
                    frame.release()
                    sink.onComplete()
                }
            }
        }
    }
}
