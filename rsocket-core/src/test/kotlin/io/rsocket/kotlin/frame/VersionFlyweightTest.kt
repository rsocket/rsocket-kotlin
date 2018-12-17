/*
 * Copyright 2015-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.internal.frame.VersionFlyweight
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFlyweightTest {
    @Test
    fun simple() {
        val version = VersionFlyweight.encode(1, 0)
        assertEquals(1, VersionFlyweight.major(version).toLong())
        assertEquals(0, VersionFlyweight.minor(version).toLong())
        assertEquals(0x00010000, version.toLong())
        assertEquals("1.0", VersionFlyweight.toString(version))
    }

    @Test
    fun complex() {
        val version = VersionFlyweight.encode(0x1234, 0x5678)
        assertEquals(0x1234, VersionFlyweight.major(version).toLong())
        assertEquals(0x5678, VersionFlyweight.minor(version).toLong())
        assertEquals(0x12345678, version.toLong())
        assertEquals("4660.22136", VersionFlyweight.toString(version))
    }
}
