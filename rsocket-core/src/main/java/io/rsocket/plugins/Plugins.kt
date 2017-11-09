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

package io.rsocket.plugins

/** JVM wide plugins for RSocket  */
object Plugins {
    private val DEFAULT = PluginRegistry()

    fun interceptConnection(interceptor: DuplexConnectionInterceptor) {
        DEFAULT.addConnectionPlugin(interceptor)
    }

    fun interceptClient(interceptor: RSocketInterceptor) {
        DEFAULT.addClientPlugin(interceptor)
    }

    fun interceptServer(interceptor: RSocketInterceptor) {
        DEFAULT.addServerPlugin(interceptor)
    }

    fun defaultPlugins(): PluginRegistry {
        return DEFAULT
    }
}
