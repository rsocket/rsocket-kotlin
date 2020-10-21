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

package io.rsocket.kotlin.transport

import io.rsocket.kotlin.*

//TODO fun interfaces don't support `suspend` functions for now... (seems will work in kotlin 1.5)

public /*fun*/ interface ClientTransport {
    @TransportApi
    public suspend fun connect(): Connection
}

@TransportApi
public inline fun ClientTransport(crossinline block: suspend () -> Connection): ClientTransport = object : ClientTransport {
    override suspend fun connect(): Connection = block()
}
