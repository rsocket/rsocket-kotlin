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

import kotlinx.atomicfu.*
import java.util.concurrent.*

class Chats {
    private val chats: MutableMap<Int, Chat> = ConcurrentHashMap()
    private val chatsId = atomic(0)

    val values: List<Chat> get() = chats.values.toList()

    fun getOrNull(id: Int): Chat? = chats[id]

    fun delete(id: Int) {
        chats -= id
    }

    fun create(name: String): Chat {
        if (chats.values.any { it.name == name }) error("Chat with such name already exists")
        val chatId = chatsId.incrementAndGet()
        val chat = Chat(chatId, name)
        chats[chatId] = chat
        return chat
    }

    fun exists(id: Int): Boolean = id in chats
}

operator fun Chats.get(id: Int): Chat = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Chats.minusAssign(id: Int): Unit = delete(id)
operator fun Chats.contains(id: Int): Boolean = exists(id)
