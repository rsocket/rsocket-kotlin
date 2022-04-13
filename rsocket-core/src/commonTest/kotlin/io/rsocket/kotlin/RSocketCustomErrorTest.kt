/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin

import kotlin.test.*

class RSocketCustomErrorTest {

    @Test
    fun shouldSuccessfullyCreate() = assertSuccessfulCreation(0x00000301)

    @Test
    fun shouldSuccessfullyCreateAtLowerBond() = assertSuccessfulCreation(0x00000301)

    @Test
    fun shouldSuccessfullyCreateNearLowerBond() = assertSuccessfulCreation(0x00000555)

    @Test
    fun shouldSuccessfullyCreateAtUpperBond() = assertSuccessfulCreation(0xFFFFFFFE.toInt())

    @Test
    fun shouldSuccessfullyCreateNearUpperBond() = assertSuccessfulCreation(0xFFFFFFAE.toInt())

    @Test
    fun shouldFailBelowLowerBond() = assertFailureCreation(0x00000300)

    @Test
    fun shouldFailOverUpperBond() = assertFailureCreation(0xFFFFFFFF.toInt())

    private fun assertSuccessfulCreation(errorCode: Int) {
        val error = RSocketError.Custom(errorCode, "custom")
        assertEquals(errorCode, error.errorCode)
    }

    private fun assertFailureCreation(errorCode: Int) {
        assertFailsWith(IllegalArgumentException::class) {
            RSocketError.Custom(errorCode, "custom")
        }
    }
}