package io.rsocket.kotlin

interface KeepAlive {

    fun keepAliveInterval(): Duration

    fun keepAliveMaxLifeTime(): Duration
}