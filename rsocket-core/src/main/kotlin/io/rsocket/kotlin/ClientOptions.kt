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

class ClientOptions {

    private var streamRequestLimit: Int = 128

    fun streamRequestLimit(streamRequestLimit: Int): ClientOptions {
        assertRequestLimit(streamRequestLimit)
        this.streamRequestLimit = streamRequestLimit
        return this
    }

    internal fun streamRequestLimit(): Int = streamRequestLimit

    fun copy(): ClientOptions = ClientOptions()
            .streamRequestLimit(streamRequestLimit)

    private fun assertRequestLimit(streamRequestLimit: Int) {
        if (streamRequestLimit <= 0) {
            throw IllegalArgumentException("stream request limit must be positive")
        }
    }
}
