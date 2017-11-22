package io.rsocket.android.exceptions

import io.rsocket.android.frame.ErrorFrameFlyweight

class ConnectionCloseException : RSocketException {
    constructor(message: String) : super(message) {}

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    override fun errorCode(): Int {
        return ErrorFrameFlyweight.CONNECTION_CLOSE
    }
}
