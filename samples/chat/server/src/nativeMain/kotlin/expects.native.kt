package io.rsocket.kotlin.samples.chat.server

import kotlin.native.concurrent.*
import kotlin.system.*

actual class Counter {
    private val atomic = AtomicInt(0)
    actual fun next(): Int = atomic.addAndGet(1)
}

actual fun currentMillis(): Long = getTimeMillis()
