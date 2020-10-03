/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import kotlinx.coroutines.flow.*

suspend fun main() {
    val api = connectToApiUsingWS("Yuri")

    api.users.all().forEach {
        println(it)
    }

    val chat = api.chats.all().firstOrNull() ?: api.chats.new("rsocket-kotlin chat")

    val sentMessage = api.messages.send(chat.id, "RSocket is awesome! (from JS)")
    println(sentMessage)

    api.messages.messages(chat.id, -1).collect {
        println("Received: $it")
    }
}
