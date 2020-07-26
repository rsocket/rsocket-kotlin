package ws

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.util.*

@OptIn(KtorExperimentalAPI::class)
actual val engine: HttpClientEngineFactory<*> = CIO

suspend fun main() = run()

