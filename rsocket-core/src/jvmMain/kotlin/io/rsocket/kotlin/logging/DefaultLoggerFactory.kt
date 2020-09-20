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

import java.util.logging.*
import java.util.logging.Level as JLevel
import java.util.logging.Logger as JLogger

actual val DefaultLoggerFactory: LoggerFactory get() = JavaLogger

class JavaLogger(override val tag: String) : Logger {
    private val jLogger = JLogger.getLogger(tag)

    private val LoggingLevel.jLevel: JLevel
        get() = when (this) {
            LoggingLevel.TRACE -> JLevel.FINEST
            LoggingLevel.DEBUG -> JLevel.FINE
            LoggingLevel.INFO -> JLevel.INFO
            LoggingLevel.WARN -> JLevel.WARNING
            LoggingLevel.ERROR -> JLevel.SEVERE
        }

    override fun isLoggable(level: LoggingLevel): Boolean = jLogger.isLoggable(level.jLevel)

    override fun rawLog(level: LoggingLevel, throwable: Throwable?, message: Any?) {
        val record = LogRecord(level.jLevel, message.toString()).apply {
            loggerName = tag        //set logger name
            sourceClassName = null  //cleanup JavaLogger name
            sourceMethodName = null //cleanup rawLog name
            thrown = throwable      //set thrown error
        }
        jLogger.log(record)
    }

    companion object : LoggerFactory {
        override fun logger(tag: String): Logger = JavaLogger(tag)
    }
}
