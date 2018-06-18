package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.rsocket.kotlin.Frame

internal class FrameSender {
    private val sender = FrameUnicastProcessor
            .create()
            .toSerialized()

    fun sent(): Flowable<Frame> = sender

    fun send(frame: Frame) {
        sender.onNext(frame)
    }
}