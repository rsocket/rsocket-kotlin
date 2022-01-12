package io.rsocket.kotlin.samples.chat.client

import kotlin.random.*

suspend fun main() {
    when (Random.nextInt(3)) {
        0 -> {
            println("Connect WS")
            connectToApiUsingWS("Oleg").use("RSocket is awesome! (from JVM WS)")
        }
        1 -> {
            println("Connect TCP")
            connectToApiUsingTCP("Hanna").use("RSocket is awesome! (from JVM TCP)")
        }
        2 -> {
            println("Connect TCP native")
            connectToApiUsingTCP("Someone", 7000).use("RSocket is awesome! (from JVM TCP)")
        }
    }
}
