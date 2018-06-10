package io.rsocket.kotlin.internal

import io.rsocket.kotlin.KeepAliveData
import java.nio.ByteBuffer

internal class EmptyKeepAliveData : KeepAliveData {

    override fun producer(): () -> ByteBuffer = noopProducer

    override fun handler(): (ByteBuffer) -> Unit = noopHandler

    companion object {
        private val emptyBuffer = ByteBuffer.allocateDirect(0)
        private val noopProducer = { emptyBuffer }
        private val noopHandler: (ByteBuffer) -> Unit = { }
    }
}