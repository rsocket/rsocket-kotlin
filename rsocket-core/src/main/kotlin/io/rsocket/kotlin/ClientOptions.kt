package io.rsocket.kotlin

class ClientOptions {

    private var streamRequestLimit: Int = 128

    fun streamRequestLimit(streamRequestLimit: Int): ClientOptions {
        assertRequestLimit(streamRequestLimit)
        this.streamRequestLimit = streamRequestLimit
        return this
    }

    internal fun streamRequestLimit(): Int = streamRequestLimit

    fun copy(): ClientOptions = ClientOptions()
            .streamRequestLimit(streamRequestLimit)

    private fun assertRequestLimit(streamRequestLimit: Int) {
        if (streamRequestLimit <= 0) {
            throw IllegalArgumentException("stream request limit must be positive")
        }
    }
}
