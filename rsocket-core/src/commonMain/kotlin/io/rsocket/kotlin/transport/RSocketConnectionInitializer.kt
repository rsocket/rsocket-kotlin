/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport

import kotlinx.coroutines.*

@RSocketTransportApi
public interface RSocketConnectionInitializer<Context : RSocketConnectionContext, T> {
    public suspend fun RSocketConnection<Context>.initializeConnection(): T
}

@RSocketTransportApi
public suspend fun <Context : RSocketConnectionContext, T> RSocketConnectionInitializer<Context, T>.runInitializer(
    connection: RSocketConnection<Context>,
): T {
    val result = connection.async {
        connection.initializeConnection()
    }
    try {
        result.join()
    } catch (cause: Throwable) {
        connection.cancel("Connection initialization cancelled", cause)
        throw cause
    }
    return result.await()
}

@RSocketTransportApi
public fun <Context : RSocketConnectionContext> RSocketConnectionInitializer<Context, Unit>.launchInitializer(
    connection: RSocketConnection<Context>,
) {
    connection.launch {
        connection.initializeConnection()
    }
}
