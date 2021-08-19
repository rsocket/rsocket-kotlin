package io.rsocket.kotlin.transport.ktor

import kotlinx.atomicfu.*
import kotlin.random.*

object PortProvider {
    private val port = atomic(Random.nextInt(20, 90) * 100)
    fun next(): Int = port.incrementAndGet()
}
