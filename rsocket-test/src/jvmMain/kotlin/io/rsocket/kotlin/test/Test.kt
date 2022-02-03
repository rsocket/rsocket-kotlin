/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.test

import io.rsocket.kotlin.logging.*
import kotlinx.coroutines.*
import java.io.*
import java.util.logging.*

internal actual fun runTest(
    ignoreNative: Boolean,
    block: suspend CoroutineScope.() -> Unit,
) {
    runBlocking(block = block)
}

actual val anotherDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual val TestLoggerFactory: LoggerFactory = run {
    //init logger
    val file = File("src/jvmTest/resources/logging.properties")
    if (file.exists()) LogManager.getLogManager().readConfiguration(file.inputStream())

    JavaLogger
}

actual fun identityHashCode(instance: Any): Int = System.identityHashCode(instance)
