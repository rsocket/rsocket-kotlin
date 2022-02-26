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

@file:Suppress("FunctionName")

package io.rsocket.kotlin.test

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.logging.*

fun TestServer(
    logging: Boolean = true,
    block: RSocketServerBuilder.() -> Unit = {}
): RSocketServer = RSocketServer {
    loggerFactory = if (logging) {
        LoggerFactory { PrintLogger.withLevel(LoggingLevel.DEBUG).logger("SERVER   |$it") }
    } else {
        NoopLogger
    }
    block()
}

fun TestConnector(
    logging: Boolean = true,
    block: RSocketConnectorBuilder.() -> Unit = {}
): RSocketConnector = RSocketConnector {
    loggerFactory = if (logging) {
        LoggerFactory { PrintLogger.withLevel(LoggingLevel.DEBUG).logger("CLIENT   |$it") }
    } else {
        NoopLogger
    }
    block()
}
