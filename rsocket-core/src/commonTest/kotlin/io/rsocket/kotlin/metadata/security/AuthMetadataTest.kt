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

package io.rsocket.kotlin.metadata.security

import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

class AuthMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeSimple() {
        val metadata = SimpleAuthMetadata("user", "password1234")
        val decoded = metadata.readLoop(SimpleAuthMetadata)

        assertEquals("user", decoded.username)
        assertEquals("password1234", decoded.password)
    }

    @Test
    fun encodeSimpleComplex() {
        val name = "𠜎𠜱𠝹𠱓𠱸𠲖𠳏𠳕𠴕𠵼𠵿𠸎"
        val metadata = SimpleAuthMetadata(name, "password1234")
        val decoded = metadata.readLoop(SimpleAuthMetadata)

        assertEquals(name, decoded.username)
        assertEquals("password1234", decoded.password)
    }

    @Test
    fun encodeSimpleVeryComplex() {
        val name = "𠜎𠜱𠝹𠱓𠱸𠲖𠳏𠳕𠴕𠵼𠵿𠸎1234567#4? "
        val metadata = SimpleAuthMetadata(name, "password1234")
        val decoded = metadata.readLoop(SimpleAuthMetadata)

        assertEquals(name, decoded.username)
        assertEquals("password1234", decoded.password)
    }

    @Test
    fun encodeBearer() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpYXQxIjoxNTE2MjM5MDIyLCJpYXQyIjoxNTE2MjM5MDIyLCJpYXQzIjoxNTE2MjM5MDIyLCJpYXQ0IjoxNTE2MjM5MDIyfQ.ljYuH-GNyyhhLcx-rHMchRkGbNsR2_4aSxo8XjrYrSM";
        val metadata = BearerAuthMetadata(token)
        val decoded = metadata.readLoop(BearerAuthMetadata)

        assertEquals(token, decoded.token)
    }

    @Test
    fun failOnLongUsername() {
        assertFailsWith(IllegalArgumentException::class) {
            SimpleAuthMetadata("x".repeat(66000), "")
        }
    }

    @Test
    fun encodeCustomAuth() {
        val metadata = RawAuthMetadata(CustomAuthType("custom/auth"), packet("hello world auth data"))
        val decoded = metadata.readLoop(RawAuthMetadata)

        assertEquals(CustomAuthType("custom/auth"), decoded.type)
        assertEquals("hello world auth data", decoded.content.readText())
    }

    @Test
    fun failOnNonAscii() {
        assertFailsWith(IllegalArgumentException::class) {
            CustomAuthType("1234567#4? 𠜎𠜱𠝹𠱓𠱸𠲖𠳏𠳕𠴕𠵼𠵿𠸎")
        }
    }

    @Test
    fun failOnLongMimeType() {
        assertFailsWith(IllegalArgumentException::class) {
            CustomAuthType("1234567890".repeat(13))
        }
    }

    @Test
    fun failOnEmptyMimeType() {
        assertFailsWith(IllegalArgumentException::class) {
            CustomAuthType("")
        }
    }

}
