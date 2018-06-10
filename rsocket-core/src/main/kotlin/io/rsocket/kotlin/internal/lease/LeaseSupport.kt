package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.LeaseRef
import io.rsocket.kotlin.internal.InterceptorRegistry

internal sealed class LeaseSupport {

    abstract fun enable(leaseHandle: (LeaseRef) -> Unit): () -> InterceptorRegistry
}

internal object ServerLeaseSupport : LeaseSupport() {

    override fun enable(leaseHandle: (LeaseRef) -> Unit)
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
                leaseHandle))
        /*enables lease for particular connection*/
        registry.connection(LeaseEnablingInterceptor(leaseContext))
        registry
    }

    private const val serverRequester = "server requester"
    private const val serverResponder = "server responder"
}

internal object ClientLeaseSupport : LeaseSupport() {
    private val leaseEnabled = LeaseContext()

    override fun enable(leaseHandle: (LeaseRef) -> Unit)
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
                leaseHandle))
        registry
    }

    private const val clientRequester = "client requester"
    private const val clientResponder = "client responder"
}
