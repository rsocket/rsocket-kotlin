package io.rsocket.exceptions

import io.rsocket.frame.ErrorFrameFlyweight

class ConnectionCloseException : RSocketException {
    constructor(message: String) : super(message) {}

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    override fun errorCode(): Int {
        return ErrorFrameFlyweight.CONNECTION_CLOSE
    }
}
