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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.metadata.*

@ExperimentalMetadataApi
public interface AuthMetadata : Metadata {
    public val type: AuthType
    public fun BytePacketBuilder.writeContent()

    override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketAuthentication

    override fun BytePacketBuilder.writeSelf() {
        writeAuthType(type)
        writeContent()
    }
}

@ExperimentalMetadataApi
public interface AuthMetadataReader<AM : AuthMetadata> : MetadataReader<AM> {
    @DangerousInternalIoApi
    public fun ByteReadPacket.readContent(type: AuthType, pool: ObjectPool<ChunkBuffer>): AM

    override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketAuthentication

    @DangerousInternalIoApi
    override fun ByteReadPacket.read(pool: ObjectPool<ChunkBuffer>): AM {
        val type = readAuthType()
        return readContent(type, pool)
    }
}
