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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

object LeaseClientServerExample {
    private val LOGGER = LoggerFactory.getLogger(LeaseClientServerExample::class.java)

    @JvmStatic
    fun main(args: Array<String>) {

        val serverLease = LeaseSupp()
        val nettyContextCloseable = RSocketFactory.receive()
                .lease { opts -> opts.leaseSupport(serverLease) }
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
        val clientLease = LeaseSupp()
        val address = nettyContextCloseable.address()
        val clientSocket = RSocketFactory.connect()
                .lease { opts -> opts.leaseSupport(clientLease) }
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
                .leaseGranter()
                .flatMapCompletable { connRef ->
                    Flowable.interval(1, 10, TimeUnit.SECONDS)
                            .flatMapCompletable { _ ->
                                connRef.grantLease(
                                        numberOfRequests = 7,
                                        ttlSeconds = 5_000,
                                        metadata = metadata("metadata"))
                            }
                }.subscribe({}, { LOGGER.error("Granter error: $it") })

        serverLease.leaseWatcher().flatMapPublisher { it.granted() }
                .subscribe(
                        {
                            LOGGER.info("Server granted Lease: " +
                                    "requests: ${it.allowedRequests}, " +
                                    "ttl: ${it.timeToLiveSeconds}, " +
                                    "metadata: ${metadata(it.metadata)}")
                        },
                        { LOGGER.error("Granted Watcher error: $it") })

        clientLease.leaseWatcher().flatMapPublisher { it.received() }
                .subscribe(
                        {
                            LOGGER.info("Client received Lease: " +
                                    "requests: ${it.allowedRequests}, " +
                                    "ttl: ${it.timeToLiveSeconds}, " +
                                    "metadata: ${metadata(it.metadata)}")
                        },
                        { LOGGER.error("Received Watcher error: $it") })


        clientSocket.onClose().blockingAwait()
    }

    private fun metadata(md: String): ByteBuffer = ByteBuffer.wrap(md.toByteArray())

    private fun metadata(md: ByteBuffer): String = StandardCharsets.UTF_8
            .decode(md).toString()

    private class LeaseSupp : (LeaseSupport) -> Unit {
        private val leaseGranter = BehaviorProcessor.create<LeaseGranter>()
        private val leaseWatcher = BehaviorProcessor.create<LeaseWatcher>()

        override fun invoke(leaseSupport: LeaseSupport) {
            leaseGranter.onNext(leaseSupport.granter())
            leaseWatcher.onNext(leaseSupport.watcher())
        }

        fun leaseGranter(): Single<LeaseGranter> = leaseGranter.firstOrError()

        fun leaseWatcher(): Single<LeaseWatcher> = leaseWatcher.firstOrError()
    }
}
