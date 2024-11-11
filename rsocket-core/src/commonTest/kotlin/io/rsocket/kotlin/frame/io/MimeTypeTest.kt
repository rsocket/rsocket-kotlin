/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.frame.io

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

class MimeTypeTest {

    private val asciiChars = '!'..'~'

    private fun randomAsciiString(length: Int): String {
        return (1..length).joinToString(separator = "") { asciiChars.random().toString() }
    }

    @Test
    fun customMimeTypeSerialization() {
        testCustomMimeType("message/x.foo")
        testCustomMimeType(randomAsciiString(1))
        testCustomMimeType(randomAsciiString(127))
        testCustomMimeType(randomAsciiString(128))
    }

    private fun testCustomMimeType(name: String) {
        val mimeType = CustomMimeType(name)
        val packet = packet {
            writeMimeType(mimeType)
        }
        assertEquals(name.length - 1, packet.peek().readByte().toInt())
        assertEquals(mimeType, packet.readMimeType())
    }
}
