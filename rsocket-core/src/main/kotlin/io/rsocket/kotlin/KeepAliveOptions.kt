package io.rsocket.kotlin

import io.rsocket.kotlin.internal.EmptyKeepAliveData

class KeepAliveOptions : KeepAlive {
    private var interval: Duration = Duration.ofMillis(100)
    private var maxLifeTime: Duration = Duration.ofSeconds(1)
    private var keepAliveData: KeepAliveData = EmptyKeepAliveData()

    fun keepAliveInterval(interval: Duration): KeepAliveOptions {
        assertDuration(interval, "keepAliveInterval")
        this.interval = interval
        return this
    }

    override fun keepAliveInterval() = interval

    fun keepAliveMaxLifeTime(maxLifetime: Duration): KeepAliveOptions {
        assertDuration(maxLifetime, "keepAliveMaxLifeTime")
        this.maxLifeTime = maxLifetime
        return this
    }

    override fun keepAliveMaxLifeTime() = maxLifeTime

    fun keepAliveData(keepAliveData: KeepAliveData): KeepAliveOptions {
        this.keepAliveData = keepAliveData
        return this
    }

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
