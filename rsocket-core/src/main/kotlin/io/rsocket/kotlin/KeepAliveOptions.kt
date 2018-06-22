package io.rsocket.kotlin

import io.rsocket.kotlin.internal.EmptyKeepAliveData

/**
 * Configures Keep-alive feature of RSocket
 */
class KeepAliveOptions : KeepAlive {
    private var interval: Duration = Duration.ofMillis(100)
    private var maxLifeTime: Duration = Duration.ofSeconds(1)
    private var keepAliveData: KeepAliveData = EmptyKeepAliveData()

    /**
     * @param interval time between KEEPALIVE frames that the client will send
     * @return this [KeepAliveOptions]
     */
    fun keepAliveInterval(interval: Duration): KeepAliveOptions {
        assertDuration(interval, "keepAliveInterval")
        this.interval = interval
        return this
    }

    /**
     * @return time between KEEPALIVE frames that the client will send
     */
    override fun keepAliveInterval() = interval

    /**
     * @param maxLifetime time that a client will allow a server to not respond to a
     * KEEPALIVE before it is assumed to be dead.
     */
    fun keepAliveMaxLifeTime(maxLifetime: Duration): KeepAliveOptions {
        assertDuration(maxLifetime, "keepAliveMaxLifeTime")
        this.maxLifeTime = maxLifetime
        return this
    }

    /**
     * @return time that a client will allow a server to not respond to a
     * KEEPALIVE before it is assumed to be dead.
     */
    override fun keepAliveMaxLifeTime() = maxLifeTime

    /**
     * Provides means to handle data sent by client with KEEPALIVE frame
     *
     * @param keepAliveData utility for handling data sent by client with KEEPALIVE
     * frame
     * @return this KeepAliveOptions
     */
    fun keepAliveData(keepAliveData: KeepAliveData): KeepAliveOptions {
        this.keepAliveData = keepAliveData
        return this
    }

    /**
     * @return utility for handling data sent by client with KEEPALIVE
     */
    fun keepAliveData(): KeepAliveData = keepAliveData

    fun copy(): KeepAliveOptions = KeepAliveOptions()
            .keepAliveInterval(interval)
            .keepAliveMaxLifeTime(maxLifeTime)
            .keepAliveData(keepAliveData)

    private fun assertDuration(duration: Duration, name: String) {
        if (duration.millis <= 0) {
            throw IllegalArgumentException("$name must be positive")
        }
        if (duration.millis > Integer.MAX_VALUE) {
            throw IllegalArgumentException("$name must not exceed 2^31-1")
        }
    }
}
