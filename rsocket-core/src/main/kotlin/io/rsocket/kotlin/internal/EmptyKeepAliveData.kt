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

package io.rsocket.kotlin.internal

import io.rsocket.kotlin.KeepAliveData
import java.nio.ByteBuffer

internal class EmptyKeepAliveData : KeepAliveData {

    override fun producer(): () -> ByteBuffer = noopProducer

    override fun handler(): (ByteBuffer) -> Unit = noopHandler

    companion object {
        private val emptyBuffer = ByteBuffer.allocateDirect(0)
        private val noopProducer = { emptyBuffer }
        private val noopHandler: (ByteBuffer) -> Unit = { }
    }
}