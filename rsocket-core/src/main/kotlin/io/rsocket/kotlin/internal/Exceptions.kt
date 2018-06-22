package io.rsocket.kotlin.internal

import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.exceptions.*
import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight

internal object Exceptions {

    /**
     * Creates [Throwable] with no stack trace
     */
    fun <T : Throwable> noStacktrace(ex: T): T {
        ex.stackTrace = arrayOf(StackTraceElement(
                ex.javaClass.name,
                "<init>",
                null,
                -1))
        return ex
    }

    /**
     * Creates [RuntimeException] from given Error [Frame]
     */
    fun from(frame: Frame): RuntimeException {
        val message = frame.dataUtf8
        val errorCode = Frame.Error.errorCode(frame)
        return when (errorCode) {
            ErrorFrameFlyweight.APPLICATION_ERROR -> ApplicationException(message)
            ErrorFrameFlyweight.CANCELED -> CancelException(message)
            ErrorFrameFlyweight.CONNECTION_CLOSE -> ConnectionCloseException(message)
            ErrorFrameFlyweight.CONNECTION_ERROR -> ConnectionException(message)
            ErrorFrameFlyweight.INVALID -> InvalidRequestException(message)
            ErrorFrameFlyweight.INVALID_SETUP -> InvalidSetupException(message)
            ErrorFrameFlyweight.REJECTED -> RejectedException(message)
            ErrorFrameFlyweight.REJECTED_RESUME -> RejectedResumeException(message)
            ErrorFrameFlyweight.REJECTED_SETUP -> RejectedSetupException(message)
            ErrorFrameFlyweight.UNSUPPORTED_SETUP -> UnsupportedSetupException(message)
            else -> InvalidRequestException(
                    "Invalid Error frame: $errorCode '$message'")
        }
    }

}
