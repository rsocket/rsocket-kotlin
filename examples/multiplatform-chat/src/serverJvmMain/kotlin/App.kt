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

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

//TODO add TCP server
fun main() {
    embeddedServer(CIO, port = 9000) {
        install(WebSockets)
        install(RSocketServerSupport)

        routing {
            rSocketChat()
        }
    }.start(true)
}

@OptIn(ExperimentalSerializationApi::class)
fun Routing.rSocketChat() {
    val proto = ConfiguredProtoBuf
    val users = Users()
    val chats = Chats()
    val messages = Messages()

    val userApi = UserApi(users)
    val chatsApi = ChatApi(chats, messages)
    val messagesApi = MessageApi(messages, chats)

    rSocket {
        val userName = payload.data.readText()
        val user = users.getOrCreate(userName)
        val session = Session(user.id)

        RSocketRequestHandler {
            fireAndForget = {
                withContext(session) {
                    when (val route = it.metadata?.readText()) {
                        null -> error("No route provided")

                        "users.deleteMe" -> userApi.deleteMe()

                        else             -> error("Wrong route: $route")
                    }
                }
            }
            requestResponse = {
                withContext(session) {
                    when (val route = it.metadata?.readText()) {
                        null -> error("No route provided")

                        "users.getMe" -> proto.encodeToPayload(userApi.getMe())
                        "users.all" -> proto.encodeToPayload(userApi.all())

                        "chats.all" -> proto.encodeToPayload(chatsApi.all())
                        "chats.new" -> proto.decoding<NewChat, Chat>(it) { (name) -> chatsApi.new(name) }
                        "chats.delete" -> proto.decoding<DeleteChat>(it) { (id) -> chatsApi.delete(id) }

                        "messages.send" -> proto.decoding<SendMessage, Message>(it) { (chatId, content) ->
                            messagesApi.send(chatId, content)
                        }
                        "messages.history" -> proto.decoding<HistoryMessages, List<Message>>(it) { (chatId, limit) ->
                            messagesApi.history(chatId, limit)
                        }

                        else               -> error("Wrong route: $route")
                    }
                }
            }
            requestStream = {
                when (val route = it.metadata?.readText()) {
                    null -> error("No route provided")

                    "messages.stream" -> {
                        val (chatId, fromMessageId) = proto.decodeFromPayload<StreamMessages>(it)
                        messagesApi.messages(chatId, fromMessageId).map { m -> proto.encodeToPayload(m) }
                    }

                    else              -> error("Wrong route: $route")
                }.flowOn(session)
            }
        }
    }
}
