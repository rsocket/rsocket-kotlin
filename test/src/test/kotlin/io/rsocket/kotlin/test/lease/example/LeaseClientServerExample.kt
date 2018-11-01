/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    private val logger = LoggerFactory.getLogger(LeaseClientServerExample::class.java)

    @JvmStatic
    fun main(args: Array<String>) {

        val serverLeaseConsumer = RSocketLeaseConsumer()
        val nettyContextCloseable = RSocketFactory.receive()
                .lease { opts -> opts.enableLease(serverLeaseConsumer) }
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
        val clientLeaseConsumer = RSocketLeaseConsumer()
        val address = nettyContextCloseable.address()
        val clientSocket = RSocketFactory.connect()
                .lease { opts -> opts.enableLease(clientLeaseConsumer) }
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
                    logger.info("Availability: ${clientSocket.availability()}")
                    clientSocket
                            .requestResponse(DefaultPayload("Client request ${Date()}"))
                            .toFlowable()
                            .doOnError { logger.info("Error: $it") }
                            .onErrorResumeNext { _: Throwable -> Flowable.empty() }
                }
                .subscribe { resp -> logger.info("Client response: ${resp.dataUtf8}") }

        serverLeaseConsumer
                .rSocketLease()
                .flatMapCompletable { rSocketLease ->
                    Flowable.interval(1, 10, TimeUnit.SECONDS)
                            .flatMapCompletable {
                                rSocketLease.granter().grant(
                                        numberOfRequests = 7,
                                        ttlSeconds = 5_000)
                            }
                }.subscribe({}, { logger.error("Granter error: $it") })

        clientSocket.onClose().blockingAwait()
    }

    private class RSocketLeaseConsumer : (RSocketLease) -> Unit {
        private val leaseSupport = BehaviorProcessor.create<RSocketLease>()

        override fun invoke(rSocketLease: RSocketLease) {
            this.leaseSupport.onNext(rSocketLease)
        }

        fun rSocketLease(): Single<RSocketLease> = this.leaseSupport.firstOrError()
    }
}
