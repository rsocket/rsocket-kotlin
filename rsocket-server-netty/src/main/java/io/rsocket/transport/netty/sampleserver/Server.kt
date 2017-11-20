package io.rsocket.transport.netty.sampleserver

import io.reactivex.Flowable
import io.rsocket.*
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.util.PayloadImpl
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

fun main(args: Array<String>) {

    RSocketFactory.receive()
            .acceptor { _, sendingSocket ->
                Mono.just(object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Mono<Payload> =
                            Mono.just(PayloadImpl("reqrep pong!"))

                    override fun requestStream(payload: Payload): Flux<Payload> =
                            Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                                    .take(5)
                                    .map { v -> PayloadImpl("reqstream pong! $v") }

                    override fun fireAndForget(payload: Payload?): Mono<Void> =
                            sendingSocket.fireAndForget(PayloadImpl("server fnf: ${Date()}"))

                    override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> =
                            Flux.from(payloads)
                                    .map { "server reqchannel: ${Date()}"}
                                    .map { PayloadImpl(it) }
                })
            }.transport(WebsocketServerTransport.create(8082)).start().block()
    Flowable.never<Void>().blockingFirst()
}