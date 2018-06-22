package io.rsocket.kotlin

/**
 * Configures lease options of client and server RSocket
 */
class LeaseOptions {

    private var leaseSupport: ((LeaseSupport) -> Unit)? = null

    /**
     * Enables lease feature of RSocket

     * @param leaseSupport consumer of [LeaseSupport] for each established connection
     * @return this [LeaseOptions]
     */
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