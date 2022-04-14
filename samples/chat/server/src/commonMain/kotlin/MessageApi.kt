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

import io.ktor.util.collections.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

class MessageApiImpl(
    private val messages: Messages,
    private val chats: Chats,
) : MessageApi {

    private val listeners = ConcurrentSet<SendChannel<Message>>()

    override suspend fun send(chatId: Int, content: String): Message {
        if (chatId !in chats) error("No chat with id '$chatId'")
        val userId = currentSession().userId
        val message = messages.create(userId, chatId, content)
        listeners.forEach { it.send(message) }
        return message
    }

    override suspend fun history(chatId: Int, limit: Int): List<Message> {
        if (chatId !in chats) error("No chat with id '$chatId'")
        return messages.takeLast(chatId, limit)
    }

    override fun messages(chatId: Int, fromMessageId: Int): Flow<Message> = flow {
        messages.takeAfter(chatId, fromMessageId).forEach { emit(it) }
        emitAll(channelFlow {
            listeners += channel
            awaitClose {
                listeners -= channel
            }
        }.buffer())
    }
}

