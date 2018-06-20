package io.rsocket.kotlin

import io.reactivex.Flowable

/**
 * Provides sources of granted (sent) and received Leases
 */
interface LeaseWatcher {

    fun received(): Flowable<Lease>

    fun granted(): Flowable<Lease>
}