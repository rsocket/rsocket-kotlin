/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.samples.chat.api.*

class Chats {
    private val storage = Storage<Chat>()

    val values: List<Chat> get() = storage.values()

    fun getOrNull(id: Int): Chat? = storage.getOrNull(id)

    fun delete(id: Int) {
        storage.remove(id)
    }

    fun create(name: String): Chat {
        if (storage.values().any { it.name == name }) error("Chat with such name already exists")
        val chatId = storage.nextId()
        val chat = Chat(chatId, name)
        storage.save(chatId, chat)
        return chat
    }

    fun exists(id: Int): Boolean = storage.contains(id)
}

operator fun Chats.get(id: Int): Chat = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Chats.minusAssign(id: Int): Unit = delete(id)
operator fun Chats.contains(id: Int): Boolean = exists(id)
