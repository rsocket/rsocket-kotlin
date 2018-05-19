package io.rsocket.kotlin.internal.fragmentation

import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor

internal class FragmentationInterceptor(private val mtu: Int) : DuplexConnectionInterceptor {
    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        source: DuplexConnection): DuplexConnection {
        return if (type == DuplexConnectionInterceptor.Type.ALL)
            FragmentationDuplexConnection(source, mtu)
        else
            source
    }
}