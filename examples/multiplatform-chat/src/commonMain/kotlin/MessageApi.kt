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
import kotlinx.serialization.*

@Serializable
data class Message(
    val id: Int,
    val chatId: Int,
    val senderId: Int,
    val timestamp: Long,
    val content: String,
)

expect class MessageApi {
    suspend fun send(chatId: Int, content: String): Message
    suspend fun history(chatId: Int, limit: Int = 10): List<Message>
    fun messages(chatId: Int, fromMessageId: Int): Flow<Message>
}

@Serializable
data class SendMessage(val chatId: Int, val content: String)

@Serializable
data class HistoryMessages(val chatId: Int, val limit: Int)

@Serializable
data class StreamMessages(val chatId: Int, val fromMessageId: Int)
