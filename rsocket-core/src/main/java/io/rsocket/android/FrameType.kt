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
package io.rsocket.android

/** Types of [Frame] that can be sent.  */
enum class FrameType(val encodedType: Int, private val flags: Int = 0) {
    // blank type that is not defined
    UNDEFINED(0x00),
    // Connection
    SETUP(0x01, Flags.CAN_HAVE_METADATA_AND_DATA),
    LEASE(0x02, Flags.CAN_HAVE_METADATA),
    KEEPALIVE(0x03, Flags.CAN_HAVE_DATA),
    // Requester to start request
    REQUEST_RESPONSE(0x04,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_REQUEST_TYPE or
                    Flags.IS_FRAGMENTABLE),
    FIRE_AND_FORGET(0x05,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_REQUEST_TYPE or
                    Flags.IS_FRAGMENTABLE),
    REQUEST_STREAM(
            0x06,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_REQUEST_TYPE or
                    Flags.HAS_INITIAL_REQUEST_N
                    or Flags.IS_FRAGMENTABLE),
    REQUEST_CHANNEL(
            0x07,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_REQUEST_TYPE or
                    Flags.HAS_INITIAL_REQUEST_N or
                    Flags.IS_FRAGMENTABLE),
    // Requester mid-stream
    REQUEST_N(0x08),
    CANCEL(0x09, Flags.CAN_HAVE_METADATA),
    // Responder
    PAYLOAD(0x0A, Flags.CAN_HAVE_METADATA_AND_DATA),
    ERROR(0x0B, Flags.CAN_HAVE_METADATA_AND_DATA),
    // Requester & Responder
    METADATA_PUSH(0x0C, Flags.CAN_HAVE_METADATA),
    // Resumption frames, not yet implemented
    RESUME(0x0D),
    RESUME_OK(0x0E),
    // synthetic types from Responder for use by the rest of the machinery
    NEXT(0xA0,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_FRAGMENTABLE),
    COMPLETE(0xB0),
    NEXT_COMPLETE(0xC0,
            Flags.CAN_HAVE_METADATA_AND_DATA or
                    Flags.IS_FRAGMENTABLE),
    EXT(0xFFFF, Flags.CAN_HAVE_METADATA_AND_DATA);

    private object Flags {

        internal const val CAN_HAVE_DATA = 1
        internal const val CAN_HAVE_METADATA = 2
        internal const val CAN_HAVE_METADATA_AND_DATA = 3
        internal const val IS_REQUEST_TYPE = 4
        internal const val HAS_INITIAL_REQUEST_N = 8
        internal const val IS_FRAGMENTABLE = 16
    }

    val isFragmentable = Flags.IS_FRAGMENTABLE == (flags and Flags.IS_FRAGMENTABLE)

    val isRequestType: Boolean = Flags.IS_REQUEST_TYPE == (flags and Flags.IS_REQUEST_TYPE)

    fun hasInitialRequestN(): Boolean = Flags.HAS_INITIAL_REQUEST_N == flags and Flags.HAS_INITIAL_REQUEST_N

    fun canHaveData(): Boolean = Flags.CAN_HAVE_DATA == flags and Flags.CAN_HAVE_DATA

    fun canHaveMetadata(): Boolean = Flags.CAN_HAVE_METADATA == flags and Flags.CAN_HAVE_METADATA

    // TODO: offset of metadata and data (simplify parsing) naming: endOfFrameHeaderOffset()
    fun payloadOffset(): Int = 0

    companion object {

        private var typesById: Array<FrameType?>

        /* Index types by id for indexed lookup. */
        init {
            var max = 0

            for (t in values()) {
                max = Math.max(t.encodedType, max)
            }

            typesById = arrayOfNulls(max + 1)

            for (t in values()) {
                typesById[t.encodedType] = t
            }
        }

        fun from(id: Int): FrameType? = typesById[id]
    }
}
