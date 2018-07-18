/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal.fragmentation

import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class FramesReassembler : Disposable {

    private val frameReassemblers = ConcurrentHashMap<Int, StreamFramesReassembler>()
    private val disposed = AtomicBoolean()

    fun shouldReassemble(frame: Frame): Boolean = frame.isFragmentable

    fun reassemble(frame: Frame): Flowable<Frame> =
            when {
                hasMoreFragments(frame) -> append(frame)
                else -> Flowable.just(
                        complete(frame)
                                ?.append(frame)?.reassemble()
                                ?: frame)
            }

    private fun hasMoreFragments(frame: Frame) = frame
            .isFlagSet(FrameHeaderFlyweight.FLAGS_F)

    private fun append(frame: Frame): Flowable<Frame> {
        val reassembler = frameReassemblers
                .getOrPut(frame.streamId) { StreamFramesReassembler(frame) }
        reassembler.append(frame)
        return Flowable.empty()
    }

    private fun complete(frame: Frame): StreamFramesReassembler? =
            frameReassemblers.remove(frame.streamId)

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            frameReassemblers.values.forEach { it.dispose() }
            frameReassemblers.clear()
        }
    }

    override fun isDisposed(): Boolean = disposed.get()
}