package dev.whyoleg.rsocket.internal

import java.util.concurrent.*

internal actual fun <V> concurrentMap(): MutableMap<Int, V> = ConcurrentHashMap()
