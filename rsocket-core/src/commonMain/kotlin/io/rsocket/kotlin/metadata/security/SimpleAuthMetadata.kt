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

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*

@ExperimentalMetadataApi
public class SimpleAuthMetadata(
    public val username: String,
    public val password: String,
) : AuthMetadata {

    init {
        require(username.length < 65535) { "Username length must be in range 1..65535 but was '${username.length}'" }
    }

    override val type: AuthType get() = AuthType.WellKnown.Simple

    override fun BytePacketBuilder.writeContent() {
        val length = username.encodeToByteArray()
        writeShort(length.size.toShort())
        writeText(username)
        writeText(password)
    }

    override fun close(): Unit = Unit

    public companion object Reader : AuthMetadataReader<SimpleAuthMetadata> {
        override fun ByteReadPacket.readContent(type: AuthType, pool: ObjectPool<ChunkBuffer>): SimpleAuthMetadata {
            require(type == AuthType.WellKnown.Simple) { "Metadata auth type should be 'simple'" }
            val length = readShort().toInt()
            val username = readTextExactBytes(length)
            val password = readText()
            return SimpleAuthMetadata(username, password)
        }
    }
}
