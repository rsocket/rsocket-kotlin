package dev.whyoleg.rsocket.flow

interface RequestStrategy {
    val initialRequest: Int
    suspend fun requestOnEmit(): Int

    companion object {
        //TODO check for best default strategy
        val Default: () -> RequestStrategy = { BufferStrategy(256) }
    }
}

inline fun RequestStrategy(initialRequest: Int, crossinline requestOnEmit: () -> Int = { 0 }): RequestStrategy = object : RequestStrategy {
    override val initialRequest: Int = initialRequest
    override suspend fun requestOnEmit(): Int = requestOnEmit()
}
