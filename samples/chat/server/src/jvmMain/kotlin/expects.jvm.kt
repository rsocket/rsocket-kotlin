package io.rsocket.kotlin.samples.chat.server

import java.util.concurrent.atomic.*

actual class Counter {
    private val atomic = AtomicInteger(0)
    actual fun next(): Int = atomic.incrementAndGet()
}

actual fun currentMillis(): Long = System.currentTimeMillis()
