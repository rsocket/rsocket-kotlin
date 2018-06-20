package io.rsocket.kotlin

class LeaseOptions {

    private var leaseSupport: ((LeaseSupport) -> Unit)? = null

    fun leaseSupport(leaseSupport: (LeaseSupport) -> Unit): LeaseOptions {
        this.leaseSupport = leaseSupport
        return this
    }

    fun leaseSupport(): ((LeaseSupport) -> Unit)? = leaseSupport

    internal fun copy(): LeaseOptions {
        val opts = LeaseOptions()
        leaseSupport?.let {
            opts.leaseSupport(it)
        }
        return opts
    }
}