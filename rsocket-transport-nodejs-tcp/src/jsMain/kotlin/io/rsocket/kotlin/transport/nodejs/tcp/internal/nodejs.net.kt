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

@file:JsModule("net")
@file:JsNonModule

package io.rsocket.kotlin.transport.nodejs.tcp.internal

import org.khronos.webgl.*

internal external fun connect(port: Int, host: String): Socket
internal external fun createServer(connectionListener: (socket: Socket) -> Unit): Server

internal external interface Server {
    fun listen(port: Int, hostname: String)
    fun close(callback: (err: Error) -> Unit)
    fun on(event: String /* "close" */, listener: () -> Unit)
}

internal external interface Socket {
    fun write(buffer: Uint8Array): Boolean
    fun destroy(error: Error = definedExternally)
    fun on(event: String /* "close" */, listener: (had_error: Boolean) -> Unit): Socket
    fun on(event: String /* "data" */, listener: (data: Uint8Array) -> Unit): Socket
    fun on(event: String /* "error" */, listener: (err: Error) -> Unit): Socket
}
