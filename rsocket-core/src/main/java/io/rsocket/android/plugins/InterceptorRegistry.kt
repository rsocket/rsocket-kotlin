/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.android.plugins

import io.rsocket.android.DuplexConnection
import io.rsocket.android.RSocket
import java.util.ArrayList

internal class InterceptorRegistry : InterceptorOptions {
    private val connections = ArrayList<DuplexConnectionInterceptor>()
    private val requesters = ArrayList<RSocketInterceptor>()
    private val handlers = ArrayList<RSocketInterceptor>()

    constructor()

    constructor(interceptorRegistry: InterceptorRegistry) {
        this.connections.addAll(interceptorRegistry.connections)
        this.requesters.addAll(interceptorRegistry.requesters)
        this.handlers.addAll(interceptorRegistry.handlers)
    }

    fun copyWith(action: (InterceptorRegistry) -> Unit): InterceptorRegistry {
        val copy = InterceptorRegistry(this)
        action(copy)
        return copy
    }

    override fun connection(interceptor: DuplexConnectionInterceptor) {
        connections.add(interceptor)
    }

    fun connectionFirst(interceptor: DuplexConnectionInterceptor) {
        connections.add(0, interceptor)
    }

    override fun requester(interceptor: RSocketInterceptor) {
        requesters.add(interceptor)
    }

    override fun handler(interceptor: RSocketInterceptor) {
        handlers.add(interceptor)
    }

    fun interceptRequester(rSocket: RSocket): RSocket {
        var rs = rSocket
        for (interceptor in requesters) {
            rs = interceptor(rs)
        }
        return rs
    }

    fun interceptHandler(rSocket: RSocket): RSocket {
        var rs = rSocket
        for (interceptor in handlers) {
            rs = interceptor(rs)
        }
        return rs
    }

    fun interceptConnection(
            type: DuplexConnectionInterceptor.Type,
            connection: DuplexConnection): DuplexConnection {
        var conn = connection
        for (interceptor in connections) {
            conn = interceptor(type, conn)
        }
        return conn
    }
}
