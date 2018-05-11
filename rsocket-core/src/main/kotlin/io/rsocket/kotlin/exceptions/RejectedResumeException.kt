package io.rsocket.kotlin.exceptions

import io.rsocket.kotlin.internal.frame.ErrorFrameFlyweight

class RejectedResumeException : RSocketException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    override fun errorCode(): Int {
        return ErrorFrameFlyweight.REJECTED_RESUME
    }
}
