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

package io.rsocket.kotlin.logging

import io.rsocket.kotlin.*

@RSocketLoggingApi
public enum class LoggingLevel { TRACE, DEBUG, INFO, WARN, ERROR }

@RSocketLoggingApi
public fun interface LoggerFactory {
    public fun logger(tag: String): Logger
}

@RSocketLoggingApi
internal expect val DefaultLoggerFactory: LoggerFactory

@RSocketLoggingApi
public interface Logger {
    public val tag: String
    public fun isLoggable(level: LoggingLevel): Boolean
    public fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?)
}

@RSocketLoggingApi
public inline fun Logger.log(level: LoggingLevel, throwable: Throwable? = null, message: () -> Any?) {
    if (!isLoggable(level)) return

    val msg = try {
        message()
    } catch (e: Throwable) {
        "Log message creation failed: $e"
    }
    rawLog(level, throwable, msg)
}

@RSocketLoggingApi
public inline fun Logger.trace(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.TRACE, throwable, message)
}

@RSocketLoggingApi
public inline fun Logger.debug(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.DEBUG, throwable, message)
}

@RSocketLoggingApi
public inline fun Logger.info(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.INFO, throwable, message)
}

@RSocketLoggingApi
public inline fun Logger.warn(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.WARN, throwable, message)
}

@RSocketLoggingApi
public inline fun Logger.error(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.ERROR, throwable, message)
}
