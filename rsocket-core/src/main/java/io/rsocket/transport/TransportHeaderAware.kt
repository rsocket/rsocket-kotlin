package io.rsocket.transport

import java.util.function.Supplier

/**
 * Extension interface to support Transports with headers at the transport layer, e.g. Websockets,
 * Http2.
 */
interface TransportHeaderAware {
    fun setTransportHeaders(transportHeaders: () -> Map<String, String>)
}
