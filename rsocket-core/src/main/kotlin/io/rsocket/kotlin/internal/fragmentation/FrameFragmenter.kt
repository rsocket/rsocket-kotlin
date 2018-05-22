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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_F
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.payloadLength
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.sliceFrameData
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.sliceFrameMetadata
import java.util.concurrent.atomic.AtomicBoolean

internal class FrameFragmenter(private val mtu: Int) {

    fun shouldFragment(frame: Frame): Boolean =
            mtu > 0 && frame.isFragmentable
                    && payloadLength(frame.content()) > mtu

    fun fragment(frame: Frame): Flowable<Frame> = Flowable
            .generate(
                    { State(frame) },
                    FragmentGenerator(),
                    { it.dispose() })

    private inner class FragmentGenerator : (State, Emitter<Frame>) -> Unit {

        override fun invoke(state: State, sink: Emitter<Frame>) {
            val dataLength = state.dataReadableBytes()

            if (state.metadataPresent()) {
                val metadataLength = state.metadataReadableBytes()
                if (metadataLength > mtu) {
                    sink.onNext(state.sliceMetadata(
                            mtu,
                            FLAGS_F))
                } else if (dataLength > mtu - metadataLength) {
                    sink.onNext(state.sliceDataAndMetadata(
                            metadataLength,
                            mtu - metadataLength,
                            FLAGS_F))
                } else {
                    sink.onNext(
                            state.sliceDataAndMetadata(
                                    metadataLength,
                                    dataLength,
                                    0))
                    sink.onComplete()
                }

            } else if (dataLength > mtu) {
                sink.onNext(state.sliceData(mtu, FLAGS_F))
            } else {
                sink.onNext(state.sliceData(dataLength, 0))
                sink.onComplete()
            }

        }
    }

    private class State(private val frame: Frame) : Disposable {
        private val disposed = AtomicBoolean()
        private val data: ByteBuf = sliceFrameData(frame.content())
        private val metadata: ByteBuf? =
                if (frame.hasMetadata())
                    sliceFrameMetadata(frame.content())
                else null

        override fun isDisposed(): Boolean = disposed.get()

        override fun dispose() {
            if (disposed.compareAndSet(false, true)) {
                frame.release()
            }
        }

        fun metadataPresent(): Boolean = metadata != null

        fun metadataReadableBytes(): Int = metadata!!.readableBytes()

        fun dataReadableBytes(): Int = data.readableBytes()

        fun sliceMetadata(metadataLength: Int, additionalFlags: Int): Frame =
                Frame.Fragmentation.sliceFrame(
                        frame,
                        metadata!!.readSlice(metadataLength),
                        Unpooled.EMPTY_BUFFER,
                        additionalFlags)

        fun sliceDataAndMetadata(metadataLength: Int,
                                 dataLength: Int,
                                 additionalFlags: Int): Frame =
                Frame.Fragmentation.sliceFrame(
                        frame,
                        metadata!!.readSlice(metadataLength),
                        data.readSlice(dataLength),
                        additionalFlags)

        fun sliceData(dataLength: Int,
                      additionalFlags: Int): Frame =
                Frame.Fragmentation.sliceFrame(
                        frame,
                        null,
                        data.readSlice(dataLength),
                        additionalFlags)
    }
}
