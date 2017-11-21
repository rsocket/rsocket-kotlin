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
package io.rsocket

import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M

import io.rsocket.android.frame.SetupFrameFlyweight
import java.nio.ByteBuffer

/**
 * Exposed to server for determination of RequestHandler based on mime types and SETUP metadata/data
 */
abstract class ConnectionSetupPayload : Payload {

    abstract fun metadataMimeType(): String

    abstract fun dataMimeType(): String

    abstract val flags: Int

    fun willClientHonorLease(): Boolean {
        return Frame.isFlagSet(flags, HONOR_LEASE)
    }

    fun doesClientRequestStrictInterpretation(): Boolean {
        return STRICT_INTERPRETATION == flags and STRICT_INTERPRETATION
    }

    override fun hasMetadata(): Boolean {
        return Frame.isFlagSet(flags, FLAGS_M)
    }

    private class ConnectionSetupPayloadImpl(
            private val metadataMimeType: String,
            private val dataMimeType: String,
            override val data: ByteBuffer,
            override val metadata: ByteBuffer,
            override val flags: Int) : ConnectionSetupPayload() {

        init {

            if (!hasMetadata() && metadata.remaining() > 0) {
                throw IllegalArgumentException("metadata flag incorrect")
            }
        }

        override fun metadataMimeType(): String {
            return metadataMimeType
        }

        override fun dataMimeType(): String {
            return dataMimeType
        }
    }

    companion object {

        val NO_FLAGS = 0
        val HONOR_LEASE = SetupFrameFlyweight.FLAGS_WILL_HONOR_LEASE
        val STRICT_INTERPRETATION = SetupFrameFlyweight.FLAGS_STRICT_INTERPRETATION

        fun create(metadataMimeType: String, dataMimeType: String): ConnectionSetupPayload {
            return ConnectionSetupPayloadImpl(
                    metadataMimeType, dataMimeType, Frame.NULL_BYTEBUFFER, Frame.NULL_BYTEBUFFER, NO_FLAGS)
        }

        fun create(
                metadataMimeType: String, dataMimeType: String, payload: Payload): ConnectionSetupPayload {
            return ConnectionSetupPayloadImpl(
                    metadataMimeType,
                    dataMimeType,
                    payload.data,
                    payload.metadata,
                    if (payload.hasMetadata()) FLAGS_M else 0)
        }

        fun create(
                metadataMimeType: String, dataMimeType: String, flags: Int): ConnectionSetupPayload {
            return ConnectionSetupPayloadImpl(
                    metadataMimeType, dataMimeType, Frame.NULL_BYTEBUFFER, Frame.NULL_BYTEBUFFER, flags)
        }

        fun create(setupFrame: Frame): ConnectionSetupPayload {
            Frame.ensureFrameType(FrameType.SETUP, setupFrame)
            return ConnectionSetupPayloadImpl(
                    Frame.Setup.metadataMimeType(setupFrame),
                    Frame.Setup.dataMimeType(setupFrame),
                    setupFrame.data,
                    setupFrame.metadata,
                    Frame.Setup.getFlags(setupFrame))
        }
    }
}
