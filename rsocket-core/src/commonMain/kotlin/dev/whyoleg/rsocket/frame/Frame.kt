package dev.whyoleg.rsocket.frame

import dev.whyoleg.rsocket.frame.io.*
import io.ktor.utils.io.core.*

private const val FlagsMask: Int = 1023
private const val FrameTypeShift: Int = 10

abstract class Frame(open val type: FrameType) {
    abstract val streamId: Int
    abstract val flags: Int

    protected abstract fun Output.writeSelf()

    fun toByteArray(): ByteArray {
        check(type.canHaveMetadata || !(flags check Flags.Metadata)) { "bad value for metadata flag" }
        return buildPacket {
            writeInt(streamId)
            writeShort((type.encodedType shl FrameTypeShift or flags).toShort())
            writeSelf()
        }.readBytes()
    }
}

fun ByteArray.toFrame(): Frame = ByteReadPacket(this).run {
    val streamId = readInt()
    val typeAndFlags = readShort().toInt() and 0xFFFF
    val flags = typeAndFlags and FlagsMask
    when (val type = FrameType(typeAndFlags shr FrameTypeShift)) {
        //stream id = 0
        FrameType.Setup -> readSetup(flags)
        FrameType.Resume -> readResume()
        FrameType.ResumeOk -> readResumeOk()
        FrameType.MetadataPush -> readMetadataPush()
        FrameType.Lease -> readLease(flags)
        FrameType.KeepAlive -> readKeepAlive(flags)
        //stream id != 0
        FrameType.Cancel -> CancelFrame(streamId)
        FrameType.Error -> readError(streamId)
        FrameType.RequestN -> readRequestN(streamId)
        FrameType.Extension -> readExtension(streamId, flags)
        FrameType.Payload,
        FrameType.RequestFnF,
        FrameType.RequestResponse
        -> readRequest(type, streamId, flags, withInitial = false)
        FrameType.RequestStream,
        FrameType.RequestChannel
        -> readRequest(type, streamId, flags, withInitial = true)
        FrameType.Reserved -> error("Reserved")
    }
}
