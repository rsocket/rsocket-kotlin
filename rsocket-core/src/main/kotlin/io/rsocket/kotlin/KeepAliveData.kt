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

import java.nio.ByteBuffer

/**
 * Provides means to handle data sent by client with KEEPALIVE frame
 */
interface KeepAliveData {

    /**
     * @return supplier of Keep-alive data [ByteBuffer] sent by client
     */
    fun producer(): () -> ByteBuffer

    /**
     * @return consumer of Keep-alive data [ByteBuffer] returned by server
     */
    fun handler(): (ByteBuffer) -> Unit
}

