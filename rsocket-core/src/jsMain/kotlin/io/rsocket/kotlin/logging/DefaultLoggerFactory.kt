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

internal actual val DefaultLoggerFactory: LoggerFactory get() = ConsoleLogger

class ConsoleLogger(
    override val tag: String,
    private val minLevel: LoggingLevel = LoggingLevel.INFO,
) : Logger {
    override fun isLoggable(level: LoggingLevel): Boolean = level >= minLevel
    override fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?) {
        val meta = "[$level] ($tag)"
        when (level) {
            LoggingLevel.ERROR -> console.error(meta, message, throwable)
            LoggingLevel.WARN -> console.warn(meta, message, throwable)
            LoggingLevel.INFO -> console.info(meta, message, throwable)
            LoggingLevel.DEBUG -> console.log(meta, message, throwable)
            LoggingLevel.TRACE -> console.log(meta, message, throwable)
        }
    }

    companion object : LoggerFactory {
        override fun logger(tag: String): Logger = ConsoleLogger(tag)

        fun witLevel(minLevel: LoggingLevel): LoggerFactory = LoggerFactory { ConsoleLogger(it, minLevel) }
    }
}
