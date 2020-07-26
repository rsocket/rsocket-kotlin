package ws

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual val engine: HttpClientEngineFactory<*> = Js

suspend fun main() = run()
