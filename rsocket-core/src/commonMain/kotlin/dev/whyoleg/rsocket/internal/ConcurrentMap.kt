package dev.whyoleg.rsocket.internal

internal expect fun <V> concurrentMap(): MutableMap<Int, V>
