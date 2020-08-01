package io.rsocket.internal

actual fun <V> concurrentMap(): MutableMap<Int, V> = mutableMapOf()
