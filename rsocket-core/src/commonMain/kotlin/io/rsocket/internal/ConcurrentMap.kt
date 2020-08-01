package io.rsocket.internal

internal expect fun <V> concurrentMap(): MutableMap<Int, V>
