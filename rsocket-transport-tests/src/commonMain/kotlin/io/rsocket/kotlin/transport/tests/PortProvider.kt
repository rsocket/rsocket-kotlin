package io.rsocket.kotlin.transport.tests

import kotlinx.atomicfu.*
import kotlin.random.*

object PortProvider {
    private val port = atomic(Random.nextInt(20, 90) * 100)
    fun next(): Int = port.incrementAndGet()

    val testServerTcp = 8000
    val testServerWebSocket = 9000
}
