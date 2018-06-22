package io.rsocket.kotlin

import io.rsocket.kotlin.internal.lease.LeaseConnection

/**
 * Provides means to grant leases to peer, and receive leases from peer.
 */
class LeaseSupport internal constructor(private val leaseConnection
                                        : LeaseConnection) {
    /**
     * @return [LeaseGranter] which is used to grant leases to peer
     */
    fun granter(): LeaseGranter = leaseConnection

    /**
     * @return [LeaseWatcher] which is used to observe leases granted to peer,
     * and received from peer
     */
    fun watcher(): LeaseWatcher = leaseConnection
}