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

package io.rsocket.kotlin.transport.nodejs.tcp.internal

import org.khronos.webgl.*

internal fun Socket.on(
    onData: (data: Uint8Array) -> Unit,
    onError: (error: Error) -> Unit,
    onClose: (hadError: Boolean) -> Unit
) {
    on("data", onData)
    on("error", onError)
    on("close", onClose)
}

internal fun createServer(
    port: Int,
    hostname: String,
    onClose: () -> Unit,
    listener: (Socket) -> Unit
): Server = createServer(listener).apply {
    on("close", onClose)
    listen(port, hostname)
}
