package io.rsocket.kotlin

import java.util.concurrent.TimeUnit

/**
 * Represents duration with different time units
 */
data class Duration(private val value: Long, val unit: TimeUnit) {

    val millis = unit.toMillis(value)

    val intMillis = unit.toMillis(value).toInt()

    companion object {

        fun ofSeconds(n: Long) = Duration(n, TimeUnit.SECONDS)

        fun ofSeconds(n: Int) = Duration(n.toLong(), TimeUnit.SECONDS)

        fun ofMinutes(n: Long) = Duration(n, TimeUnit.MINUTES)

        fun ofMinutes(n: Int) = Duration(n.toLong(), TimeUnit.MINUTES)

        fun ofMillis(n: Long) = Duration(n, TimeUnit.MILLISECONDS)

        fun ofMillis(n: Int) = Duration(n.toLong(), TimeUnit.MILLISECONDS)
    }
}

