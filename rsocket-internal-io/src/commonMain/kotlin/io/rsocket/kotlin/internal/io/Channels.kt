/*
 * Copyright 2015-2023 the original author or authors.
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

package io.rsocket.kotlin.internal.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*

private val onUndeliveredCloseable: (Closeable) -> Unit = Closeable::close

public fun <E : Closeable> channelForCloseable(capacity: Int): Channel<E> =
    Channel(capacity, onUndeliveredElement = onUndeliveredCloseable)

public fun Channel<out Closeable>.cancelWithCause(cause: Throwable?) {
    close(cause) // close channel to provide right cause
    cancel() // force call of onUndeliveredElement to release buffered elements
}
