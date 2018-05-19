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

import io.reactivex.Completable
import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import org.reactivestreams.Publisher

/** Fragments and Re-assembles frames. MTU is number of bytes per fragment.*/
internal class FragmentationDuplexConnection(private val source: DuplexConnection,
                                             mtu: Int)
    : DuplexConnection {
    private val framesReassembler = FramesReassembler()
    private val frameFragmenter: FrameFragmenter = FrameFragmenter(mtu)

    override fun send(frame: Publisher<Frame>): Completable =
            Flowable.fromPublisher(frame)
                    .concatMapCompletable { f -> sendOne(f) }

    override fun sendOne(frame: Frame): Completable {
        return if (frameFragmenter.shouldFragment(frame)) {
            source.send(frameFragmenter.fragment(frame))
        } else {
            source.sendOne(frame)
        }
    }

    override fun receive(): Flowable<Frame> = source
            .receive()
            .concatMap { frame ->
                if (framesReassembler.shouldReassemble(frame)) {
                    framesReassembler.reassemble(frame)
                } else {
                    Flowable.just(frame)
                }
            }

    override fun availability(): Double = source.availability()

    override fun close(): Completable = source.close()

    override fun onClose(): Completable = source
            .onClose()
            .doOnTerminate {
                framesReassembler.dispose()
            }
}
