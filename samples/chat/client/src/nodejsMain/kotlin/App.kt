/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.*

suspend fun main() {
    coroutineScope {
        // native WS server is incompatible with js WS client
        (Servers.ALL - Servers.Native.WS).forEach {
            val client = ApiClient(it, "Kolya")
            launch {
                client.use(it, "RSocket is awesome! (from nodeJS)")
            }
        }
    }
}
