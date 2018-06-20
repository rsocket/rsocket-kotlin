package io.rsocket.kotlin

import io.rsocket.kotlin.internal.lease.LeaseConnection

class LeaseSupport internal constructor(private val leaseConnection
                                        : LeaseConnection) {

    fun granter(): LeaseGranter = leaseConnection

    fun watcher(): LeaseWatcher = leaseConnection
}