package io.rsocket.android.exceptions

import io.rsocket.android.frame.ErrorFrameFlyweight

class RejectedResumeException : RSocketException {
    constructor(message: String) : super(message) {}

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    override fun errorCode(): Int {
        return ErrorFrameFlyweight.REJECTED_RESUME
    }
}
