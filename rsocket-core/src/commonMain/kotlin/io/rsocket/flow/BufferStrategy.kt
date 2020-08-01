package io.rsocket.flow

import kotlinx.coroutines.flow.*

class BufferStrategy(private val buffer: Int) : RequestStrategy {
    override val initialRequest: Int = buffer
    private var current = buffer

    override suspend fun requestOnEmit(): Int {
        return if (--current == 0) {
            current += buffer
            buffer
        } else 0
    }
}

fun <T> RequestingFlow<T>.requestingBy(buffer: Int): Flow<T> = requesting { BufferStrategy(buffer) }
