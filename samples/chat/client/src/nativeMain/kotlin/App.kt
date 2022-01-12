package io.rsocket.kotlin.samples.chat.client

import kotlinx.coroutines.*
import kotlin.random.*

fun main() = runBlocking {
    when (Random.nextInt(2)) {
        0 -> {
            println("Connect TCP")
            connectToApiUsingTCP("Gloria").use("RSocket is awesome! (from Native TCP)")
        }
        1 -> {
            println("Connect TCP native")
            connectToApiUsingTCP("Kolya", 7000).use("RSocket is awesome! (from Native TCP)")
        }
    }
}
