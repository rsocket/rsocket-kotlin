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
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.ktor.*
import io.rsocket.kotlin.transport.ktor.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

@OptIn(KtorExperimentalAPI::class, ExperimentalSerializationApi::class, ExperimentalMetadataApi::class, InternalAPI::class)
fun main() {
    val proto = ConfiguredProtoBuf
    val users = Users()
    val chats = Chats()
    val messages = Messages()

    val userApi = UserApi(users)
    val chatsApi = ChatApi(chats, messages)
    val messagesApi = MessageApi(messages, chats)

    val rSocketServer = RSocketServer()

    fun Payload.route(): String = metadata?.read(RoutingMetadata)?.tags?.first() ?: error("No route provided")

    //create acceptor
    val acceptor = ConnectionAcceptor {
        val userName = config.setupPayload.data.readText()
        val user = users.getOrCreate(userName)
        val session = Session(user.id)

        RSocketRequestHandler {
            fireAndForget {
                withContext(session) {
                    when (val route = it.route()) {
                        "users.deleteMe" -> userApi.deleteMe()

                        else             -> error("Wrong route: $route")
                    }
                }
            }
            requestResponse {
                withContext(session) {
                    when (val route = it.route()) {
                        "users.getMe"      -> proto.encodeToPayload(userApi.getMe())
                        "users.all"        -> proto.encodeToPayload(userApi.all())

                        "chats.all"        -> proto.encodeToPayload(chatsApi.all())
                        "chats.new"        -> proto.decoding<NewChat, Chat>(it) { (name) -> chatsApi.new(name) }
                        "chats.delete"     -> proto.decoding<DeleteChat>(it) { (id) -> chatsApi.delete(id) }

                        "messages.send"    -> proto.decoding<SendMessage, Message>(it) { (chatId, content) ->
                            messagesApi.send(chatId, content)
                        }
                        "messages.history" -> proto.decoding<HistoryMessages, List<Message>>(it) { (chatId, limit) ->
                            messagesApi.history(chatId, limit)
                        }

                        else               -> error("Wrong route: $route")
                    }
                }
            }
            requestStream {
                when (val route = it.route()) {
                    "messages.stream" -> {
                        val (chatId, fromMessageId) = proto.decodeFromPayload<StreamMessages>(it)
                        messagesApi.messages(chatId, fromMessageId).map { m -> proto.encodeToPayload(m) }
                    }

                    else              -> error("Wrong route: $route")
                }.flowOn(session)
            }
        }
    }

    //start TCP server
    rSocketServer.bind(TcpServerTransport(ActorSelectorManager(Dispatchers.IO), port = 9000), acceptor)

    //start WS server
    embeddedServer(CIO, port = 9000) {
        install(WebSockets)
        install(RSocketSupport) {
            server = rSocketServer
        }

        routing {
            rSocket(acceptor = acceptor)
        }
    }.start(true)
}
