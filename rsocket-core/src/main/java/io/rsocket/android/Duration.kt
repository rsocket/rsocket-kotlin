package io.rsocket

import java.util.concurrent.TimeUnit

/**
 * Created by Maksym Ostroverkhov on 27.10.17.
 */

data class Duration(val value: Long, val unit: TimeUnit) {

    val toMillis = unit.toMillis(value)

    companion object {
        val ZERO = Duration(0, TimeUnit.MILLISECONDS)
        fun ofSeconds(n: Long) = Duration(n, TimeUnit.SECONDS)
    }
}

