package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.interceptors.RSocketInterceptor

internal class LeaseInterceptor(private val leaseContext: LeaseContext,
                                private val leaseManager: LeaseManager,
                                private val tag: String)
    : RSocketInterceptor {

    override fun invoke(rSocket: RSocket): RSocket =
            LeaseRSocket(
                    leaseContext,
                    rSocket,
                    tag,
                    leaseManager)
}
