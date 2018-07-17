/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

