package io.rsocket.kotlin.internal.util

internal fun reactiveStreamsRequestN(initialRequestN: Int) =
        if (initialRequestN == Int.MAX_VALUE) {
            Long.MAX_VALUE
        } else {
            initialRequestN.toLong()
        }
