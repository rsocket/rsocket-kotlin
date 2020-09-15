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
import java.time.*
import java.util.concurrent.*

class Messages {
    private val messages: MutableMap<Int, Message> = ConcurrentHashMap()
    private val messagesId = atomic(0)

    val values: List<Message> get() = messages.values.toList()

    fun getOrNull(id: Int): Message? = messages[id]

    fun delete(id: Int) {
        messages -= id
    }

    fun deleteForChat(chatId: Int) {
        messages -= messages.filterValues { it.chatId == chatId }.keys
    }

    fun create(userId: Int, chatId: Int, content: String): Message {
        val messageId = messagesId.incrementAndGet()
        val message = Message(messageId, chatId, userId, Instant.now().epochSecond, content)
        messages[messageId] = message
        return message
    }

    private fun byChatSorted(chatId: Int): List<Message> = values.filter { it.chatId == chatId }.sortedByDescending { it.timestamp }

    fun takeLast(chatId: Int, limit: Int): List<Message> = byChatSorted(chatId).take(limit)

    fun takeAfter(chatId: Int, messageId: Int): List<Message> {
        val messages = byChatSorted(chatId)
        if (messageId == -1) return messages.take(1)

        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) error("No message with id '$messageId'")
        return messages.drop(index)
    }
}

operator fun Messages.get(id: Int): Message = getOrNull(id) ?: error("No user with id '$id' exists")
operator fun Messages.minusAssign(id: Int): Unit = delete(id)
