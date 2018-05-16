package io.rsocket.android.fragmentation

import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.android.Frame
import io.rsocket.android.frame.FrameHeaderFlyweight
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