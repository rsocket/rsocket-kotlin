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

package io.rsocket.kotlin.frame.io

import io.ktor.utils.io.core.*

internal class Version(val major: Int, val minor: Int) {
    val intValue: Int get() = (major shl 16) or (minor and 0xFFFF)

    override fun equals(other: Any?): Boolean = other is Version && intValue == other.intValue
    override fun hashCode(): Int = intValue
    override fun toString(): String = "$major.$minor"

    companion object {
        val Current: Version = Version(1, 0)
    }
}

internal fun ByteReadPacket.readVersion(): Version {
    val value = readInt()
    return Version(
        major = value shr 16 and 0xFFFF,
        minor = value and 0xFFFF
    )
}

internal fun BytePacketBuilder.writeVersion(version: Version) {
    writeInt(version.intValue)
}
