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

package io.rsocket.kotlin.frame.io

import kotlin.test.*

class VersionTest {

    @Test
    fun simple() {
        val version = Version(1, 0)
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
        assertEquals(0x00010000, version.value)
        assertEquals("1.0", version.toString())
    }

    @Test
    fun complex() {
        val version = Version(0x1234, 0x5678)
        assertEquals(0x1234, version.major)
        assertEquals(0x5678, version.minor)
        assertEquals(0x12345678, version.value)
        assertEquals("4660.22136", version.toString())
    }

    @Test
    fun noShortOverflow() {
        val version = Version(43210, 43211)
        assertEquals(43210, version.major)
        assertEquals(43211, version.minor)
    }
}
