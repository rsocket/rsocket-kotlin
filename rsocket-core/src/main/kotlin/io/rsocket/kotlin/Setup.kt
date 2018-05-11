/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.kotlin

import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_M

import io.rsocket.kotlin.internal.frame.SetupFrameFlyweight
import java.nio.ByteBuffer

/**
 * Exposed to server for determination of RequestHandler based on mime types
 * and SETUP metadata/data
 */
abstract class Setup : Payload, KeepAlive {

    abstract fun metadataMimeType(): String

    abstract fun dataMimeType(): String

    abstract val flags: Int

    fun willClientHonorLease(): Boolean = Frame.isFlagSet(flags, HONOR_LEASE)

    override fun hasMetadata(): Boolean = Frame.isFlagSet(flags, FLAGS_M)

    private class SetupImpl(
            private val metadataMimeType: String,
            private val dataMimeType: String,
            override val data: ByteBuffer,
            override val metadata: ByteBuffer,
            private val keepAliveInterval: Int,
            private val keepAliveLifetime: Int,
            override val flags: Int) : Setup() {

        init {
            if (!hasMetadata() && metadata.remaining() > 0) {
                throw IllegalArgumentException("metadata flag incorrect")
            }
        }

        override fun keepAliveInterval(): Duration =
                Duration.ofMillis(keepAliveInterval)

        override fun keepAliveMaxLifeTime(): Duration =
                Duration.ofMillis(keepAliveLifetime)

        override fun metadataMimeType(): String = metadataMimeType

        override fun dataMimeType(): String = dataMimeType
    }

    companion object {

        private const val HONOR_LEASE = SetupFrameFlyweight.FLAGS_WILL_HONOR_LEASE

        internal fun create(setupFrame: Frame): Setup {
            Frame.ensureFrameType(FrameType.SETUP, setupFrame)
            return try {
                SetupImpl(
                        Frame.Setup.metadataMimeType(setupFrame),
                        Frame.Setup.dataMimeType(setupFrame),
                        setupFrame.data,
                        setupFrame.metadata,
                        Frame.Setup.keepaliveInterval(setupFrame),
                        Frame.Setup.maxLifetime(setupFrame),
                        Frame.Setup.getFlags(setupFrame))
            } finally {
                setupFrame.release()
            }
        }
    }
}
