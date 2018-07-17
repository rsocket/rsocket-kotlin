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

package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.exceptions.MissingLeaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import java.nio.ByteBuffer

class LeaseRSocketTest {

    private lateinit var leaseRSocket: LeaseRSocket
    private lateinit var rSocket: MockRSocket
    private lateinit var leaseManager: LeaseManager

    @Before
    fun setUp() {
        rSocket = MockRSocket()
        leaseManager = LeaseManager("")
        val leaseEnabled = LeaseContext()
        leaseRSocket = LeaseRSocket(leaseEnabled, rSocket, "", leaseManager)
    }

    @Test
    fun grantedLease() {
        leaseManager.grant(2, 1_000, EMPTY_DATA)
        assertEquals(1.0, leaseRSocket.availability(), 1e-5)
    }

    @Test
    fun usedLease() {
        leaseManager.grant(2, 1_000, EMPTY_DATA)
        leaseRSocket.fireAndForget(DefaultPayload("test")).subscribe()
        assertEquals(0.5, leaseRSocket.availability(), 1e-5)
    }

    @Test
    fun depletedLease() {
        leaseManager.grant(1, 1_000, EMPTY_DATA)
        val fireAndForget = leaseRSocket.fireAndForget(DefaultPayload("test"))
        val firstErr = fireAndForget.blockingGet()
        assertTrue(firstErr == null)
        val secondErr = fireAndForget.blockingGet()
        assertTrue(secondErr is MissingLeaseException)
    }

    @Test
    fun connectionNotAvailable() {
        leaseManager.grant(1, 1_000, EMPTY_DATA)
        rSocket.setAvailability(0.0f)
        assertEquals(0.0, leaseRSocket.availability(), 1e-5)
    }

    private class MockRSocket : RSocket {
        private var availability = 1.0f

        fun setAvailability(availability: Float) {
            this.availability = availability
        }

        override fun availability(): Double = availability.toDouble()

        override fun fireAndForget(payload: Payload): Completable =
                Completable.complete()

        override fun requestResponse(payload: Payload): Single<Payload> =
                Single.just(payload)

        override fun requestStream(payload: Payload): Flowable<Payload> =
                Flowable.just(payload)

        override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
                Flowable.fromPublisher(payloads)

        override fun metadataPush(payload: Payload): Completable =
                Completable.complete()

        override fun close(): Completable = Completable.complete()

        override fun onClose(): Completable = Completable.complete()
    }

    companion object {
        private val EMPTY_DATA = ByteBuffer.allocateDirect(0)
    }
}