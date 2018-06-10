package io.rsocket.kotlin.internal.lease

import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor
import io.rsocket.kotlin.util.DuplexConnectionProxy

internal class LeaseEnablingInterceptor(private val leaseContext: LeaseContext)
    : DuplexConnectionInterceptor {

    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        connection: DuplexConnection)
            : DuplexConnection =
            if (type === DuplexConnectionInterceptor.Type.SETUP) {
                LeaseEnablingConnection(connection, leaseContext)
            } else {
                connection
            }

    private class LeaseEnablingConnection(
            setupConnection: DuplexConnection,
            private val leaseContext: LeaseContext)
        : DuplexConnectionProxy(setupConnection) {

        override fun receive(): Flowable<Frame> {
            return super.receive()
                    .doOnNext(
                            { f ->
                                val enabled = f.type == FrameType.SETUP
                                        && Frame.Setup.leaseEnabled(f)
                                leaseContext.leaseEnabled = enabled
                            })
        }
    }
}


