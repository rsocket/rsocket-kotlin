package io.rsocket.android.fragmentation

import io.rsocket.android.DuplexConnection
import io.rsocket.android.plugins.DuplexConnectionInterceptor

internal class FragmentationInterceptor(private val mtu: Int) : DuplexConnectionInterceptor {
    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        source: DuplexConnection): DuplexConnection {
        return if (type == DuplexConnectionInterceptor.Type.ALL)
            FragmentationDuplexConnection(source, mtu)
        else
            source
    }
}