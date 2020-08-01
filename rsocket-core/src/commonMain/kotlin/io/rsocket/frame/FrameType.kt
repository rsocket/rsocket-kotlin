package io.rsocket.frame

import io.rsocket.frame.io.*

enum class FrameType(val encodedType: Int, flags: Int = Flags.Empty) {
    Reserved(0x00),

    //CONNECTION
    Setup(0x01, Flags.CanHaveData or Flags.CanHaveMetadata),
    Lease(0x02, Flags.CanHaveMetadata),
    KeepAlive(0x03, Flags.CanHaveData),

    // METADATA
    MetadataPush(0x0C, Flags.CanHaveMetadata),

    //REQUEST
    RequestFnF(0x05, Flags.CanHaveData or Flags.CanHaveMetadata or Flags.Fragmentable or Flags.Request),
    RequestResponse(0x04, Flags.CanHaveData or Flags.CanHaveMetadata or Flags.Fragmentable or Flags.Request),
    RequestStream(0x06, Flags.CanHaveMetadata or Flags.CanHaveData or Flags.HasInitialRequest or Flags.Fragmentable or Flags.Request),
    RequestChannel(0x07, Flags.CanHaveMetadata or Flags.CanHaveData or Flags.HasInitialRequest or Flags.Fragmentable or Flags.Request),

    // DURING REQUEST
    RequestN(0x08),
    Cancel(0x09),

    // RESPONSE
    Payload(0x0A, Flags.CanHaveData or Flags.CanHaveMetadata or Flags.Fragmentable),
    Error(0x0B, Flags.CanHaveData),

    // RESUMPTION
    Resume(0x0D),
    ResumeOk(0x0E),

    Extension(0x3F, Flags.CanHaveData or Flags.CanHaveMetadata);

    val hasInitialRequest: Boolean = flags check Flags.HasInitialRequest
    val isRequestType: Boolean = flags check Flags.Request
    val isFragmentable: Boolean = flags check Flags.Fragmentable
    val canHaveMetadata: Boolean = flags check Flags.CanHaveMetadata
    val canHaveData: Boolean = flags check Flags.CanHaveData

    private object Flags {
        const val Empty = 0
        const val HasInitialRequest = 1
        const val Request = 2
        const val Fragmentable = 4
        const val CanHaveMetadata = 8
        const val CanHaveData = 16
    }

    companion object {
        private val encodedTypes: Array<FrameType?>

        init {
            val maximumEncodedType = values().map(FrameType::encodedType).max() ?: 0
            encodedTypes = arrayOfNulls(maximumEncodedType + 1)
            values().forEach { encodedTypes[it.encodedType] = it }
        }

        operator fun invoke(encodedType: Int): FrameType =
            encodedTypes[encodedType] ?: throw IllegalArgumentException("Frame type $encodedType is unknown")
    }
}
