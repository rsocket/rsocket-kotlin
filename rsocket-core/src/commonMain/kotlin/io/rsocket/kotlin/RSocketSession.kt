package io.rsocket.kotlin

import kotlinx.coroutines.*

//calling cancel will cause ConnectionError sent (same as if coroutine launched on this scope)
public sealed interface RSocketSession : CoroutineScope {
    //TODO: add session state later
    // public val state: StateFlow<SessionState>
}
