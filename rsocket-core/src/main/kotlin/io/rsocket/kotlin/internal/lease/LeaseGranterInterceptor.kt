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

package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.LeaseSupport
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor

internal class LeaseGranterInterceptor(
        private val leaseContext: LeaseContext,
        private val sender: LeaseManager,
        private val receiver: LeaseManager,
        private val leaseHandle: (LeaseSupport) -> Unit)
    : DuplexConnectionInterceptor {

    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        connection: DuplexConnection): DuplexConnection {
        return if (type === DuplexConnectionInterceptor.Type.SERVICE) {
            val leaseConnection = LeaseConnection(
                    leaseContext,
                    connection,
                    sender,
                    receiver)
            val leaseSupport = LeaseSupport(leaseConnection)
            leaseHandle(leaseSupport)
            leaseConnection
        } else {
            connection
        }
    }
}
