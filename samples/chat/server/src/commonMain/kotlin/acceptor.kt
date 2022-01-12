package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
fun acceptor(): ConnectionAcceptor {
    val proto = ConfiguredProtoBuf
    val users = Users()
    val chats = Chats()
    val messages = Messages()

    val userApi = UserApiImpl(users)
    val chatsApi = ChatApiImpl(chats, messages)
    val messagesApi = MessageApiImpl(messages, chats)

    return ConnectionAcceptor {
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
}
