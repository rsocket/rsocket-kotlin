/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.core

import io.rsocket.kotlin.*

@Suppress("DEPRECATION_ERROR")
public class InterceptorsBuilder internal constructor() {
    private val requesters = mutableListOf<Interceptor<RSocket>>()
    private val responders = mutableListOf<Interceptor<RSocket>>()
    private val connections = mutableListOf<Interceptor<Connection>>()
    private val acceptors = mutableListOf<Interceptor<ConnectionAcceptor>>()

    public fun forRequester(interceptor: Interceptor<RSocket>) {
        requesters += interceptor
    }

    public fun forResponder(interceptor: Interceptor<RSocket>) {
        responders += interceptor
    }

    @Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated without replacement")
    public fun forConnection(interceptor: Interceptor<Connection>) {
        connections += interceptor
    }

    public fun forAcceptor(interceptor: Interceptor<ConnectionAcceptor>) {
        acceptors += interceptor
    }

    internal fun build(): Interceptors = Interceptors(requesters, responders, connections, acceptors)
}

@Suppress("DEPRECATION_ERROR")
internal class Interceptors(
    private val requesters: List<Interceptor<RSocket>>,
    private val responders: List<Interceptor<RSocket>>,
    private val connections: List<Interceptor<Connection>>,
    private val acceptors: List<Interceptor<ConnectionAcceptor>>,
) {
    fun wrapRequester(requester: RSocket): RSocket = requesters.fold(requester) { r, i -> i.intercept(r) }
    fun wrapResponder(responder: RSocket): RSocket = responders.fold(responder) { r, i -> i.intercept(r) }
    fun wrapConnection(connection: Connection): Connection = connections.fold(connection) { c, i -> i.intercept(c) }
    fun wrapAcceptor(connection: ConnectionAcceptor): ConnectionAcceptor =
        acceptors.fold(connection) { c, i -> i.intercept(c) }
}
