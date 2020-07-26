package dev.whyoleg.rsocket.error

sealed class RSocketError(val errorCode: Int, message: String) : Throwable(message) {

    sealed class Setup(errorCode: Int, message: String) : RSocketError(errorCode, message) {
        class Invalid(message: String) : Setup(ErrorCode.InvalidSetup, message)
        class Unsupported(message: String) : Setup(ErrorCode.UnsupportedSetup, message)
        class Rejected(message: String) : Setup(ErrorCode.RejectedSetup, message)
    }

    class RejectedResume(message: String) : RSocketError(ErrorCode.RejectedResume, message)

    class ConnectionError(message: String) : RSocketError(ErrorCode.ConnectionError, message)
    class ConnectionClose(message: String) : RSocketError(ErrorCode.ConnectionClose, message)

    class ApplicationError(message: String) : RSocketError(ErrorCode.ApplicationError, message)
    class Rejected(message: String) : RSocketError(ErrorCode.Rejected, message)
    class Canceled(message: String) : RSocketError(ErrorCode.Canceled, message)
    class Invalid(message: String) : RSocketError(ErrorCode.Invalid, message)

    class Custom(errorCode: Int, message: String) : RSocketError(errorCode, message) {
        init {
            require(errorCode >= ErrorCode.CustomMin || errorCode <= ErrorCode.CustomMax) {
                "Allowed errorCode value should be in range [0x00000301-0xFFFFFFFE]"
            }
        }
    }
}

//TODO format error codes 0x%08X or use enum
internal fun RSocketError(streamId: Int, errorCode: Int, message: String): Throwable =
    when (streamId) {
        0 -> when (errorCode) {
            ErrorCode.InvalidSetup -> RSocketError.Setup.Invalid(message)
            ErrorCode.UnsupportedSetup -> RSocketError.Setup.Unsupported(message)
            ErrorCode.RejectedSetup -> RSocketError.Setup.Rejected(message)
            ErrorCode.RejectedResume -> RSocketError.RejectedResume(message)
            ErrorCode.ConnectionError -> RSocketError.ConnectionError(message)
            ErrorCode.ConnectionClose -> RSocketError.ConnectionClose(message)
            else                       -> IllegalArgumentException("Invalid Error frame in Stream ID 0: $errorCode '%$message'")
        }
        else -> when (errorCode) {
            ErrorCode.ApplicationError -> RSocketError.ApplicationError(message)
            ErrorCode.Rejected -> RSocketError.Rejected(message)
            ErrorCode.Canceled -> RSocketError.Canceled(message)
            ErrorCode.Invalid -> RSocketError.Invalid(message)
            else                       -> when (errorCode >= ErrorCode.CustomMin || errorCode <= ErrorCode.CustomMax) {
                true -> RSocketError.Custom(errorCode, message)
                false -> IllegalArgumentException("Invalid Error frame in Stream ID $streamId: $errorCode '$message'")
            }
        }
    }
