package dev.whyoleg.rsocket.error

object ErrorCode {

    //stream id = 0
    const val InvalidSetup: Int = 0x00000001
    const val UnsupportedSetup: Int = 0x00000002
    const val RejectedSetup: Int = 0x00000003
    const val RejectedResume: Int = 0x00000004

    const val ConnectionError: Int = 0x00000101
    const val ConnectionClose: Int = 0x00000102

    //stream id != 0
    const val ApplicationError: Int = 0x00000201
    const val Rejected: Int = 0x00000202
    const val Canceled: Int = 0x00000203
    const val Invalid: Int = 0x00000204

    //reserved
    const val Reserved: Int = 0x00000000
    const val ReservedForExtension: Int = 0xFFFFFFFF.toInt()

    //custom error codes range
    const val CustomMin: Int = 0x00000301
    const val CustomMax: Int = 0xFFFFFFFE.toInt()
}
