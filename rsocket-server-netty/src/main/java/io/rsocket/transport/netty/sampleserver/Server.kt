package io.rsocket.transport.netty.sampleserver

import io.reactivex.Flowable
import io.rsocket.*
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.util.PayloadImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

fun main(args: Array<String>) {
    RSocketFactory.receive()
            .acceptor (
                object : SocketAcceptor {
                    override fun accept(setup: ConnectionSetupPayload, sendingSocket: RSocket): Mono<RSocket> {
                        return Mono.just(object : AbstractRSocket() {
                            override fun requestResponse(payload: Payload): Mono<Payload> = Mono.just(PayloadImpl("reqrep pong!"))

                            override fun requestStream(payload: Payload): Flux<Payload> {
                                return Flux.interval(Duration.ZERO, Duration.ofSeconds(1)).take(5).map { v -> PayloadImpl("reqstream pong! $v") }
                            }

                            override fun fireAndForget(payload: Payload?): Mono<Void> {
                                return sendingSocket.fireAndForget(PayloadImpl("server fnf: ${Date()}"))
                            }
                        })
                    }
                }
            ).transport(WebsocketServerTransport.create(8082)).start().block()
    Flowable.never<Void>().blockingFirst()
}