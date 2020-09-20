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

enum class LoggingLevel { TRACE, DEBUG, INFO, WARN, ERROR }

fun interface LoggerFactory {
    fun logger(tag: String): Logger
}

expect val DefaultLoggerFactory: LoggerFactory

interface Logger {
    val tag: String
    fun isLoggable(level: LoggingLevel): Boolean
    fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?)
}

inline fun Logger.log(level: LoggingLevel, throwable: Throwable? = null, message: () -> Any?) {
    if (!isLoggable(level)) return

    val msg = try {
        message()
    } catch (e: Throwable) {
        "Log message creation failed: $e"
    }
    rawLog(level, throwable, msg)
}

inline fun Logger.trace(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.TRACE, throwable, message)
}

inline fun Logger.debug(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.DEBUG, throwable, message)
}

inline fun Logger.info(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.INFO, throwable, message)
}

inline fun Logger.warn(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.WARN, throwable, message)
}

inline fun Logger.error(throwable: Throwable? = null, message: () -> Any?) {
    log(LoggingLevel.ERROR, throwable, message)
}
