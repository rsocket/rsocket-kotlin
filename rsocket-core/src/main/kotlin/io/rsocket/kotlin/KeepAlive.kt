package io.rsocket.kotlin

/**
 * Configures keep-alive feature of RSocket
 */
interface KeepAlive {

    /**
     * @return time between KEEPALIVE frames that the client will send
     */
    fun keepAliveInterval(): Duration

    /**
     * @return time that a client will allow a server to not respond to a
     * KEEPALIVE before it is assumed to be dead.
     */
    fun keepAliveMaxLifeTime(): Duration
}