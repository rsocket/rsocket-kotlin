package io.rsocket.kotlin

import io.reactivex.Flowable

/**
 * Provides sources of granted (sent) and received Leases
 */
interface LeaseWatcher {
    /**
     * @return [Flowable] of leases received by this side of connection, completes
     * once associated RSocket is closed
     */
    fun received(): Flowable<Lease>

    /**
     * @return [Flowable] of leases sent by this side of connection, completes
     * once associated RSocket is closed
     */
    fun granted(): Flowable<Lease>
}