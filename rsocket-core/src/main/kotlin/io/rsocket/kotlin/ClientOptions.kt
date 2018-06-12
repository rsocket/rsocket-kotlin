package io.rsocket.kotlin

class ClientOptions : Options<ClientOptions>() {

    override fun copy(): ClientOptions =
            ClientOptions().streamRequestLimit(streamRequestLimit())
}
