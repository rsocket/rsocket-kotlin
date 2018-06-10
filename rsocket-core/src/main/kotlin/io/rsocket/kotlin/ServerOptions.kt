package io.rsocket.kotlin

class ServerOptions : Options<ServerOptions>() {

    override fun copy(): ServerOptions =
            ServerOptions().streamRequestLimit(streamRequestLimit())
}
