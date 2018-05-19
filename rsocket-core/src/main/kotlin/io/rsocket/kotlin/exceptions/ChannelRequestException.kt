package io.rsocket.kotlin.exceptions

class ChannelRequestException(message: String, cause: Throwable)
    : RuntimeException(message, cause) {

    override val message: String
        get() = super.message!!

    override val cause: Throwable
        get() = super.cause!!
}