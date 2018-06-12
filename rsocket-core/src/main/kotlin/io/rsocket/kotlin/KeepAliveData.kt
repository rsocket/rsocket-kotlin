package io.rsocket.kotlin

import java.nio.ByteBuffer

interface KeepAliveData {

    fun producer(): () -> ByteBuffer

    fun handler(): (ByteBuffer) -> Unit
}

