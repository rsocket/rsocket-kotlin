package io.rsocket.kotlin

abstract class Options<T:Options<T>> internal constructor() {
    private var streamRequestLimit: Int = 128

    @Suppress("UNCHECKED_CAST")
    open fun streamRequestLimit(streamRequestLimit: Int): T {
        assertRequestLimit(streamRequestLimit)
        this.streamRequestLimit = streamRequestLimit
        return this as T
    }

    abstract fun copy(): T

    internal fun streamRequestLimit(): Int = streamRequestLimit

    private fun assertRequestLimit(streamRequestLimit: Int) {
        if (streamRequestLimit <= 0) {
            throw IllegalArgumentException("stream request limit must be positive")
        }
    }
}