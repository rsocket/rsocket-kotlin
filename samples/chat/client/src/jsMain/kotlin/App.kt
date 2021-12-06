package io.rsocket.kotlin.samples.chat.client

suspend fun main() {
    connectToApiUsingWS("Yuri").use("RSocket is awesome! (from JS WS)")
}
