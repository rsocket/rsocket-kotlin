/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin.keepalive

import kotlin.native.concurrent.*
import kotlin.time.*

@ExperimentalTime
public fun KeepAlive(
    interval: Duration = Duration.seconds(20),
    maxLifetime: Duration = Duration.seconds(90)
): KeepAlive = KeepAlive(
    intervalMillis = interval.toInt(DurationUnit.MILLISECONDS),
    maxLifetimeMillis = maxLifetime.toInt(DurationUnit.MILLISECONDS)
)

public class KeepAlive(
    public val intervalMillis: Int = 20 * 1000, // 20 seconds
    public val maxLifetimeMillis: Int = 90 * 1000 // 90 seconds
)

@SharedImmutable
internal val DefaultKeepAlive = KeepAlive(
    intervalMillis = 20 * 1000, // 20 seconds
    maxLifetimeMillis = 90 * 1000 // 90 seconds
)
