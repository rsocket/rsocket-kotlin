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

package io.rsocket.kotlin

// TODO: Make public, deciding on naming, drop Version in frame.io
internal data class RSocketProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<RSocketProtocolVersion> {

    init {
        check(major >= 0) { "Major version component should be non-negative but was $major" }
        check(minor >= 0) { "Minor version component should be non-negative: but was $minor" }
    }

    override fun compareTo(other: RSocketProtocolVersion): Int {
        return when (val majorResult = major.compareTo(other.major)) {
            0    -> minor.compareTo(other.minor)
            else -> majorResult
        }
    }

    override fun toString(): String = "$major.$minor"

    companion object {
        val V1: RSocketProtocolVersion = RSocketProtocolVersion(1, 0)
        val V2: RSocketProtocolVersion = RSocketProtocolVersion(2, 0)
    }
}
