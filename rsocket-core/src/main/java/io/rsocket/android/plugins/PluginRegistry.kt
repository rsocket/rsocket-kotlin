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

class PluginRegistry {
    private val connections = ArrayList<DuplexConnectionInterceptor>()
    private val clients = ArrayList<RSocketInterceptor>()
    private val servers = ArrayList<RSocketInterceptor>()

    constructor()

    constructor(defaults: PluginRegistry) {
        this.connections.addAll(defaults.connections)
        this.clients.addAll(defaults.clients)
        this.servers.addAll(defaults.servers)
    }

    fun addConnectionPlugin(interceptor: DuplexConnectionInterceptor) {
        connections.add(interceptor)
    }

    fun addClientPlugin(interceptor: RSocketInterceptor) {
        clients.add(interceptor)
    }

    fun addServerPlugin(interceptor: RSocketInterceptor) {
        servers.add(interceptor)
    }

    fun applyClient(rSocket: RSocket): RSocket {
        var rs = rSocket
        for (interceptor in clients) {
            rs = interceptor(rs)
        }
        return rs
    }

    fun applyServer(rSocket: RSocket): RSocket {
        var rs = rSocket
        for (interceptor in servers) {
            rs = interceptor(rs)
        }
        return rs
    }

    fun applyConnection(
            type: DuplexConnectionInterceptor.Type, connection: DuplexConnection): DuplexConnection {
        var conn = connection
        for (interceptor in connections) {
            conn = interceptor(type, conn)
        }
        return conn
    }
}
