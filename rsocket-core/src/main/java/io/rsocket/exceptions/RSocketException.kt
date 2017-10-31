package io.rsocket.exceptions

abstract class RSocketException : RuntimeException {
    constructor(message: String) : super(message) {}

    constructor(message: String, cause: Throwable) : super(message, cause) {}

    abstract fun errorCode(): Int
}
