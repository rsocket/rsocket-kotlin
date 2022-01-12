package io.rsocket.kotlin.samples.chat.server

import kotlin.coroutines.*

data class Session(val userId: Int) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Session

    companion object : CoroutineContext.Key<Session>
}

suspend fun currentSession(): Session = coroutineContext[Session]!!
