package io.rsocket.android

import java.util.concurrent.TimeUnit

/**
 * Created by Maksym Ostroverkhov on 27.10.17.
 */

data class Duration(val value: Long, val unit: TimeUnit) {

    val millis = unit.toMillis(value)

    val intMillis = unit.toMillis(value).toInt()

    companion object {

        fun ofSeconds(n: Long) = Duration(n, TimeUnit.SECONDS)

        fun ofSeconds(n: Int) = Duration(n.toLong(), TimeUnit.SECONDS)

        fun ofMillis(n: Long) = Duration(n, TimeUnit.MILLISECONDS)

        fun ofMillis(n: Int) = Duration(n.toLong(), TimeUnit.MILLISECONDS)
    }
}

