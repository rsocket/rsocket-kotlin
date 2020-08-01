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

package io.rsocket.flow

import kotlinx.coroutines.flow.*

class BufferStrategy(private val buffer: Int) : RequestStrategy {
    override val initialRequest: Int = buffer
    private var current = buffer

    override suspend fun requestOnEmit(): Int {
        return if (--current == 0) {
            current += buffer
            buffer
        } else 0
    }
}

fun <T> RequestingFlow<T>.requestingBy(buffer: Int): Flow<T> = requesting { BufferStrategy(buffer) }
