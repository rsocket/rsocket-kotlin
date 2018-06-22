package io.rsocket.kotlin

import java.nio.ByteBuffer

/**
 * Provides means to handle data sent by client with KEEPALIVE frame
 */
interface KeepAliveData {

    /**
     * @return supplier of Keep-alive data [ByteBuffer] sent by client
     */
    fun producer(): () -> ByteBuffer

    /**
     * @return consumer of Keep-alive data [ByteBuffer] returned by server
     */
    fun handler(): (ByteBuffer) -> Unit
}

