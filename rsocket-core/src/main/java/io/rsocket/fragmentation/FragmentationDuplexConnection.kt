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

import io.netty.util.collection.IntObjectHashMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.rsocket.DuplexConnection
import io.rsocket.Frame
import io.rsocket.frame.FrameHeaderFlyweight
import org.reactivestreams.Publisher

/** Fragments and Re-assembles frames. MTU is number of bytes per fragment. The default is 1024  */
class FragmentationDuplexConnection(private val source: DuplexConnection, mtu: Int) : DuplexConnection {
    private val frameReassemblers = IntObjectHashMap<FrameReassembler>()
    private val frameFragmenter: FrameFragmenter = FrameFragmenter(mtu)

    override fun availability(): Double {
        return source.availability()
    }

    override fun send(frames: Publisher<Frame>): Completable {
        return Flowable.fromPublisher(frames)
                .concatMap({ frame -> sendOne(frame).toFlowable<Void>() })
                .ignoreElements()
    }

    override fun sendOne(frame: Frame): Completable {
        return if (frameFragmenter.shouldFragment(frame)) {
            source.send(frameFragmenter.fragment(frame))
        } else {
            source.sendOne(frame)
        }
    }

    override fun receive(): Flowable<Frame> {
        return source
                .receive()
                .concatMap { frame ->
                    if (FrameHeaderFlyweight.FLAGS_F == frame.flags() and FrameHeaderFlyweight.FLAGS_F) {
                        val frameReassembler = getFrameReassembler(frame)
                        frameReassembler.append(frame)
                        return@concatMap Flowable.empty<Frame>()
                    } else if (frameReassemblersContain(frame.streamId)) {
                        val frameReassembler = removeFrameReassembler(frame.streamId)
                        frameReassembler.append(frame)
                        val reassembled = frameReassembler.reassemble()
                        return@concatMap Flowable.just<Frame>(reassembled)
                    } else {
                        return@concatMap Flowable.just<Frame>(frame)
                    }
                }
    }

    override fun close(): Completable {
        return source.close()
    }

    @Synchronized private fun getFrameReassembler(frame: Frame): FrameReassembler =
         frameReassemblers.getOrPut(frame.streamId, { FrameReassembler(frame) })


    @Synchronized private fun removeFrameReassembler(streamId: Int): FrameReassembler {
        return frameReassemblers.remove(streamId)
    }

    @Synchronized private fun frameReassemblersContain(streamId: Int): Boolean {
        return frameReassemblers.containsKey(streamId)
    }

    override fun onClose(): Completable {
        return source
                .onClose()
                .doFinally {
                    synchronized(this@FragmentationDuplexConnection) {
                        frameReassemblers.values.forEach { it.dispose() }

                        frameReassemblers.clear()
                    }
                }
    }

    companion object {

        val defaultMTU: Int
            get() = if (java.lang.Boolean.getBoolean("io.rsocket.fragmentation.enable")) {
                Integer.getInteger("io.rsocket.fragmentation.mtu", 1024)!!
            } else 0
    }
}
