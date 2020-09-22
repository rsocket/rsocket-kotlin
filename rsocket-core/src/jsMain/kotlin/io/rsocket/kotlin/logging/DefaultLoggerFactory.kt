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

actual val DefaultLoggerFactory: LoggerFactory get() = ConsoleLogger

class ConsoleLogger(
    override val tag: String,
    private val minLevel: LoggingLevel = LoggingLevel.INFO,
) : Logger {
    override fun isLoggable(level: LoggingLevel): Boolean = level >= minLevel
    override fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?) {
        val meta = "[$level] ($tag)"
        when (level) {
            LoggingLevel.ERROR -> throwable?.let { console.error(meta, message, "Error:", it) } ?: console.error(meta, message)
            LoggingLevel.WARN -> throwable?.let { console.warn(meta, message, "Error:", it) } ?: console.warn(meta, message)
            LoggingLevel.INFO -> throwable?.let { console.info(meta, message, "Error:", it) } ?: console.info(meta, message)
            LoggingLevel.DEBUG -> throwable?.let { console.log(meta, message, "Error:", it) } ?: console.log(meta, message)
            LoggingLevel.TRACE -> throwable?.let { console.log(meta, message, "Error:", it) } ?: console.log(meta, message)
        }
    }

    companion object : LoggerFactory {
        override fun logger(tag: String): Logger = ConsoleLogger(tag)

        fun withLevel(minLevel: LoggingLevel): LoggerFactory = LoggerFactory { ConsoleLogger(it, minLevel) }
    }
}
