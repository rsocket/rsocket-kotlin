package io.rsocket.kotlin


class RSocketOptions {

    private var streamRequestLimit: Int = 128

    fun streamRequestLimit(streamRequestLimit: Int): RSocketOptions {
        assertRequestLimit(streamRequestLimit)
        this.streamRequestLimit = streamRequestLimit
        return this
    }

    internal fun streamRequestLimit(): Int = streamRequestLimit

    fun copy(): RSocketOptions = RSocketOptions()
            .streamRequestLimit(streamRequestLimit)

    private fun assertRequestLimit(streamRequestLimit: Int) {
        if (streamRequestLimit <= 0) {
            throw IllegalArgumentException("stream request limit must be positive")
        }
    }
}
