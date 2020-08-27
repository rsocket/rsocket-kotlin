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

import io.ktor.utils.io.core.*

@Suppress("FunctionName")
fun Version(major: Int, minor: Int): Version = Version((major shl 16) or (minor and 0xFFFF))

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class Version(val value: Int) {
    val major: Int get() = value shr 16 and 0xFFFF
    val minor: Int get() = value and 0xFFFF
    override fun toString(): String = "$major.$minor"

    companion object {
        val Current: Version = Version(1, 0)
    }
}

fun ByteReadPacket.readVersion(): Version = Version(readInt())

fun BytePacketBuilder.writeVersion(version: Version) {
    writeInt(version.value)
}
