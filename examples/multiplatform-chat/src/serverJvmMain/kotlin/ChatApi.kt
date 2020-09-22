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

actual class ChatApi(
    private val chats: Chats,
    private val messages: Messages,
) {

    actual suspend fun all(): List<Chat> = chats.values.toList()

    actual suspend fun new(name: String): Chat = chats.create(name)

    actual suspend fun delete(id: Int) {
        messages.deleteForChat(id)
        chats -= id
    }
}

