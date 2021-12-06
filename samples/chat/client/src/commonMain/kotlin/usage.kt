package io.rsocket.kotlin.samples.chat.client

import kotlinx.coroutines.flow.*

suspend fun ApiClient.use(message: String) {

    users.all().forEach {
        println(it)
    }

    val chat = chats.all().firstOrNull() ?: chats.new("rsocket-kotlin chat")

    val sentMessage = messages.send(chat.id, message)
    println(sentMessage)

    messages.messages(chat.id, -1).collect {
        println("Received: $it")
    }
}
