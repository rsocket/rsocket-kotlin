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

import io.rsocket.kotlin.RSocketLease
import io.rsocket.kotlin.internal.InterceptorRegistry

internal sealed class LeaseFeature {

    abstract fun enable(leaseSupport: (RSocketLease) -> Unit): () -> InterceptorRegistry
}

internal object ServerLeaseFeature : LeaseFeature() {

    override fun enable(leaseSupport: (RSocketLease) -> Unit)
            : () -> InterceptorRegistry = {

        val sender = LeaseManager(serverRequester)
        val receiver = LeaseManager(serverResponder)
        val leaseContext = LeaseContext()
        val registry = InterceptorRegistry()

        /*requester RSocket is Lease aware*/
        registry.requester(LeaseInterceptor(
                leaseContext,
                sender,
                serverRequester))
        /*handler RSocket is Lease aware*/
        registry.handler(LeaseInterceptor(
                leaseContext,
                receiver,
                serverResponder))
        /*grants Lease quotas of above RSockets*/
        registry.connection(LeaseGranterInterceptor(
                leaseContext,
                sender,
                receiver,
                leaseSupport))
        /*enables lease for particular connection*/
        registry.connection(LeaseEnablingInterceptor(leaseContext))
        registry
    }

    private const val serverRequester = "server requester"
    private const val serverResponder = "server responder"
}

internal object ClientLeaseFeature : LeaseFeature() {
    private val leaseEnabled = LeaseContext()

    override fun enable(leaseSupport: (RSocketLease) -> Unit)
            : () -> InterceptorRegistry = {

        val sender = LeaseManager(clientRequester)
        val receiver = LeaseManager(clientResponder)
        val registry = InterceptorRegistry()
        /*requester RSocket is Lease aware*/
        registry.requester(LeaseInterceptor(
                leaseEnabled,
                sender, clientRequester))
        /*handler RSocket is Lease aware*/
        registry.handler(LeaseInterceptor(
                leaseEnabled,
                receiver,
                clientResponder))
        /*grants Lease quotas to above RSockets*/
        registry.connection(LeaseGranterInterceptor(
                leaseEnabled,
                sender,
                receiver,
                leaseSupport))
        registry
    }

    private const val clientRequester = "client requester"
    private const val clientResponder = "client responder"
}
