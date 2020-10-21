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

package io.rsocket.kotlin.core

import io.rsocket.kotlin.logging.*

public class RSocketServerBuilder internal constructor() {
    public var loggerFactory: LoggerFactory = DefaultLoggerFactory

    private val interceptors: InterceptorsBuilder = InterceptorsBuilder()

    public fun interceptors(configure: InterceptorsBuilder.() -> Unit) {
        interceptors.configure()
    }

    internal fun build(): RSocketServer = RSocketServer(loggerFactory, interceptors.build())
}

public fun RSocketServer(configure: RSocketServerBuilder.() -> Unit = {}): RSocketServer {
    val builder = RSocketServerBuilder()
    builder.configure()
    return builder.build()
}
