package io.rsocket.android.transport

/**
 * Extension interface to support Transports with headers at the transport layer, e.g. Websockets,
 * Http2.
 */
interface TransportHeaderAware {
    fun setTransportHeaders(transportHeaders: () -> Map<String, String>)
}
