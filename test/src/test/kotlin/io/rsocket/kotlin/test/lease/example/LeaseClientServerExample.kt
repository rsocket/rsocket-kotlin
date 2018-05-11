package io.rsocket.kotlin.test.lease.example

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

object LeaseClientServerExample {
    private val LOGGER = LoggerFactory.getLogger(LeaseClientServerExample::class.java)

    @JvmStatic
    fun main(args: Array<String>) {

        val serverLease = LeaseRefs()
        val nettyContextCloseable = RSocketFactory.receive()
                .enableLease(serverLease)
                .acceptor {
                    { _, _ ->
                        Single.just(
                                object : AbstractRSocket() {
                                    override fun requestResponse(payload: Payload)
                                            : Single<Payload> =
                                            Single.just(DefaultPayload("Server Response ${Date()}"))
                                })
                    }
                }
                .transport(TcpServerTransport.create("localhost", 0))
                .start()
                .blockingGet()

        val address = nettyContextCloseable.address()
        val clientSocket = RSocketFactory.connect()
                .enableLease(LeaseRefs())
                .keepAlive { opts ->
                    opts.keepAliveInterval(Duration.ofMinutes(1))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(20))
                }
                .transport(TcpClientTransport.create(address))
                .start()
                .blockingGet()

        Flowable.interval(1, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .flatMap {
                    LOGGER.info("Availability: ${clientSocket.availability()}")
                    clientSocket
                            .requestResponse(DefaultPayload("Client request ${Date()}"))
                            .toFlowable()
                            .doOnError { LOGGER.info("Error: $it") }
                            .onErrorResumeNext { _: Throwable ->
                                Flowable.empty<Payload>()
                            }
                }
                .subscribe { resp -> LOGGER.info("Client response: ${resp.dataUtf8}") }

        serverLease
                .leaseRef()
                .flatMapCompletable { connRef ->
                    Flowable.interval(1, 10, TimeUnit.SECONDS)
                            .flatMapCompletable { _ ->
                                connRef.grantLease(
                                        numberOfRequests = 7,
                                        timeToLiveMillis = 5_000)
                            }
                }.subscribe()

        clientSocket.onClose().blockingAwait()
    }

    private class LeaseRefs : (LeaseRef) -> Unit {
        private val leaseRefs = BehaviorProcessor.create<LeaseRef>()

        fun leaseRef(): Single<LeaseRef> = leaseRefs.firstOrError()

        override fun invoke(leaseRef: LeaseRef) {
            leaseRefs.onNext(leaseRef)
        }
    }
}
