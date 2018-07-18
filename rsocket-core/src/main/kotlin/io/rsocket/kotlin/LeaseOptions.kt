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

/**
 * Configures lease options of client and server RSocket
 */
class LeaseOptions {

    private var leaseSupport: ((LeaseSupport) -> Unit)? = null

    /**
     * Enables lease feature of RSocket

     * @param leaseSupport consumer of [LeaseSupport] for each established connection
     * @return this [LeaseOptions]
     */
    fun leaseSupport(leaseSupport: (LeaseSupport) -> Unit): LeaseOptions {
        this.leaseSupport = leaseSupport
        return this
    }

    fun leaseSupport(): ((LeaseSupport) -> Unit)? = leaseSupport

    internal fun copy(): LeaseOptions {
        val opts = LeaseOptions()
        leaseSupport?.let {
            opts.leaseSupport(it)
        }
        return opts
    }
}