package io.rsocket.android.util

import io.rsocket.android.Duration

interface KeepAlive {

    fun keepAliveInterval(): Duration

    fun keepAliveMaxLifeTime(): Duration
}